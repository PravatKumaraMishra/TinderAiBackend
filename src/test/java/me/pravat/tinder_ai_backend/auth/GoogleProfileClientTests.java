package me.pravat.tinder_ai_backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.profile.Gender;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GoogleProfileClientTests {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleProfileClient client = new GoogleProfileClient("web-client", builder.build());

    private void token(String audience, int expiry) {
        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?access_token=google-token"))
                .andRespond(withSuccess("{\"aud\":\"" + audience + "\",\"expires_in\":" + expiry + "}", MediaType.APPLICATION_JSON));
    }
    private void user(boolean verified) {
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(header("Authorization", "Bearer google-token"))
                .andRespond(withSuccess("{\"sub\":\"subject\",\"email\":\"ALICE@gmail.com\",\"email_verified\":" + verified
                        + ",\"given_name\":\"Alice\",\"family_name\":\"Example\",\"picture\":\"https://lh3.googleusercontent.com/photo\"}", MediaType.APPLICATION_JSON));
    }
    @Test void importsGoogleDetailsAutomatically() {
        token("web-client", 3600); user(true);
        int year = java.time.LocalDate.now(java.time.ZoneOffset.UTC).getYear() - 25;
        server.expect(requestTo("https://people.googleapis.com/v1/people/me?personFields=birthdays,genders"))
                .andExpect(header("Authorization", "Bearer google-token"))
                .andRespond(withSuccess("{\"birthdays\":[{\"date\":{\"year\":" + year + ",\"month\":1,\"day\":1}}],\"genders\":[{\"value\":\"female\"}]}", MediaType.APPLICATION_JSON));
        var profile = client.fetch("google-token").profile("id");
        assertEquals("Alice", profile.firstName()); assertEquals(25, profile.age());
        assertEquals(Gender.FEMALE, profile.gender()); assertEquals("https://lh3.googleusercontent.com/photo", profile.imageUrl());
        server.verify();
    }
    @Test void missingPeoplePermissionDoesNotBlockSignIn() {
        token("web-client", 3600); user(true);
        server.expect(requestTo("https://people.googleapis.com/v1/people/me?personFields=birthdays,genders"))
                .andRespond(withForbiddenRequest());
        var identity = client.fetch("google-token");
        assertEquals("alice@gmail.com", identity.email()); assertEquals(0, identity.age()); assertNull(identity.gender());
        server.verify();
    }
    @Test void rejectsTokensForAnotherApp() {
        token("other-client", 3600);
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> client.fetch("google-token")).getStatusCode().value());
        server.verify();
    }
    @Test void rejectsExpiredToken() {
        token("web-client", 0);
        assertThrows(ResponseStatusException.class, () -> client.fetch("google-token")); server.verify();
    }
    @Test void rejectsUnverifiedEmail() {
        token("web-client", 3600); user(false);
        assertThrows(ResponseStatusException.class, () -> client.fetch("google-token")); server.verify();
    }
}
