package me.pravat.tinder_ai_backend.storage;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ImageStorageTests {
    @Test void uploadsBytesWithContentTypeAndReturnsPublicObjectUrl() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var path = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        var body = new AtomicReference<byte[]>();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var storage = new ImageStorageService("http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-key", "test-secret", "pictures", "https://images.example.com/");
            String url = storage.userPhoto("alice", "data:image/jpeg;base64,/9j/", null);
            assertTrue(path.get().startsWith("/pictures/users/alice/"));
            assertTrue(url.startsWith("https://images.example.com/users/alice/"));
            assertTrue(url.endsWith(".jpg"));
            assertEquals("image/jpeg", contentType.get());
            assertArrayEquals(new byte[]{(byte) 255, (byte) 216, (byte) 255}, body.get());
            String aiUrl = storage.upload("ai-profiles/persona", new byte[]{1, 2, 3}, ".png");
            assertTrue(aiUrl.startsWith("https://images.example.com/ai-profiles/persona/"));
            assertEquals("image/png", contentType.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void retainsOnlyExistingPhotoAndRequiresStorageForNewUploads() {
        var storage = new ImageStorageService("", "", "", "", "");
        assertEquals("", storage.userPhoto("alice", "", null));
        assertEquals("legacy.png", storage.userPhoto("alice", "legacy.png", "legacy.png"));
        assertThrows(ResponseStatusException.class,
                () -> storage.userPhoto("alice", "https://other.example/bob.jpg", "legacy.png"));
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> storage.userPhoto("alice", "data:image/jpeg;base64,/9j/", null)).getStatusCode().value());
    }
}
