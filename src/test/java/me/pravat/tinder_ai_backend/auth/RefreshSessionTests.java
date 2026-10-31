package me.pravat.tinder_ai_backend.auth;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.users.UserRepository;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class RefreshSessionTests {
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final TokenService tokens = mock(TokenService.class);
    final UserRepository users = mock(UserRepository.class);
    final RefreshSessionService service = new RefreshSessionService(mongo, tokens, mock(JwtDecoder.class), users,
            Duration.ofDays(7), true, "Lax");
    MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("tinder_refresh", "family.secret"));
        return request;
    }
    @Test void rotationUsesAtomicUnexpiredHashMatchAndKeepsAbsoluteExpiry() {
        var expiry = Instant.now().plusSeconds(1000);
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(RefreshSessionService.RefreshSession.class)))
                .thenReturn(new RefreshSessionService.RefreshSession("family", "alice", "hash", List.of(), expiry));
        when(users.existsById("alice")).thenReturn(true);
        when(tokens.issue("alice")).thenReturn(new TokenService.TokenResponse("new", "Bearer", 1800));
        var response = new MockHttpServletResponse();
        assertEquals("new", service.refresh(request(), response).accessToken());
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax")); assertFalse(cookie.contains("family.secret;"));
        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(query.capture(), any(Update.class), eq(RefreshSessionService.RefreshSession.class));
        assertTrue(query.getValue().getQueryObject().containsKey("expiresAt"));
        assertTrue(query.getValue().getQueryObject().containsKey("currentHash"));
    }
    @Test void rejectedRefreshClearsCookieAndRevokesOnlyRecognizedUsedSecret() {
        var response = new MockHttpServletResponse();
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.refresh(request(), response)).getStatusCode().value());
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq(RefreshSessionService.RefreshSession.class));
        assertTrue(query.getValue().getQueryObject().containsKey("usedHashes"));
        verifyNoInteractions(tokens);
    }
    @Test void logoutDeletesSessionAndClearsCookie() {
        var response = new MockHttpServletResponse();
        service.logout(request(), response);
        verify(mongo).remove(any(Query.class), eq(RefreshSessionService.RefreshSession.class));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }
}
