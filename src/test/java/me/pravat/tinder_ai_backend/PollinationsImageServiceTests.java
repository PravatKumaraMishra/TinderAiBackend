package me.pravat.tinder_ai_backend;

import me.pravat.tinder_ai_backend.profile.PollinationsImageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PollinationsImageServiceTests {
    private static final String MODEL = "community/scriptsnsenses-sys/flux-2-dev-free";
    private static final String FREE_CATALOG = """
            [{"name":"community/scriptsnsenses-sys/flux-2-dev-free",
            "paid_only":false,"pricing":{"currency":"pollen"}}]
            """;

    @Test void checksPricingThenDownloadsImageWithEncodedPromptAndSecretHeader() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertEquals("https://gen.pollinations.ai/image/models", request.getURI().toString()))
                .andRespond(withSuccess(FREE_CATALOG, MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("GET", request.getMethod().name());
            assertEquals("Bearer test-key", request.getHeaders().getFirst("Authorization"));
            assertTrue(request.getURI().getRawPath().contains("portrait%20%26%20%3F%20%2F%20%23"));
            assertTrue(request.getURI().getRawQuery().contains("model=community%2Fscriptsnsenses-sys%2Fflux-2-dev-free"));
            assertFalse(request.getURI().toString().contains("test-key"));
        }).andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_JPEG));
        var image = new PollinationsImageService(builder.build(), "test-key", MODEL).generate("portrait & ? / #");
        assertArrayEquals(new byte[]{1, 2, 3}, image.bytes());
        assertEquals(".jpg", image.extension());
        server.verify();
    }

    @Test void refusesPricedPaidOnlyMissingAndUnknownPricingModelsBeforeGeneration() {
        for (String catalog : new String[]{"[]",
                FREE_CATALOG.replace("false", "true"),
                FREE_CATALOG.replace("\"currency\":\"pollen\"", "\"currency\":\"pollen\",\"completionImageTokens\":\"0.002\""),
                FREE_CATALOG.replace("{\"currency\":\"pollen\"}", "null")}) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(request -> {}).andRespond(withSuccess(catalog, MediaType.APPLICATION_JSON));
            var service = new PollinationsImageService(builder.build(), "test-key", MODEL);
            assertThrows(IllegalStateException.class, () -> service.generate("portrait"));
            server.verify();
        }
    }

    @Test void quotaFailureReportsStatus() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {}).andRespond(withSuccess(FREE_CATALOG, MediaType.APPLICATION_JSON));
        server.expect(request -> {}).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        var service = new PollinationsImageService(builder.build(), "test-key", MODEL);
        assertEquals("Pollinations image request failed (HTTP 429)",
                assertThrows(IllegalStateException.class, () -> service.generate("portrait")).getMessage());
        server.verify();
    }

    @Test void rejectsNonImageResponse() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {}).andRespond(withSuccess(FREE_CATALOG, MediaType.APPLICATION_JSON));
        server.expect(request -> {}).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var service = new PollinationsImageService(builder.build(), "test-key", MODEL);
        assertThrows(IllegalStateException.class, () -> service.generate("portrait"));
        server.verify();
    }

    @Test void missingKeyFailsBeforeCallingProvider() {
        var service = new PollinationsImageService(RestClient.create(), "", MODEL);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.generate("portrait"))
                .getMessage().contains("POLLINATIONS_API_KEY"));
    }
}
