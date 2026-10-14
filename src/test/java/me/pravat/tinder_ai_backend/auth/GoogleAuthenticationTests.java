package me.pravat.tinder_ai_backend.auth;

import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import com.mongodb.client.result.UpdateResult;
import me.pravat.tinder_ai_backend.users.*;
import me.pravat.tinder_ai_backend.profile.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoogleAuthenticationTests {
    private static KeyPair keys;
    private GoogleTokenVerifier verifier;
    @BeforeAll static void createKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); keys = generator.generateKeyPair();
    }
    @BeforeEach void setup() {
        verifier = new GoogleTokenVerifier("web-client", NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    }
    private String credential(String issuer, String audience, Instant expiry, boolean verified, KeyPair signingKeys) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("google-subject")
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(300)).expiresAt(expiry)
                .claim("email", "ALICE@gmail.com").claim("email_verified", verified).build();
        return NimbusJwtEncoder.withKeyPair((RSAPublicKey) signingKeys.getPublic(), (RSAPrivateKey) signingKeys.getPrivate())
                .build().encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
    @Test void acceptsOnlyValidGoogleIdentity() {
        for (String issuer : List.of("accounts.google.com", "https://accounts.google.com")) {
            var identity = verifier.verify(credential(issuer, "web-client", Instant.now().plusSeconds(300), true, keys));
            assertEquals("google-subject", identity.subject());
            assertEquals("alice@gmail.com", identity.email());
        }
    }
    @Test void rejectsWrongIssuerAudienceExpiredAndUnverifiedCredentials() {
        for (String token : List.of(
                credential("https://attacker.example", "web-client", Instant.now().plusSeconds(300), true, keys),
                credential("accounts.google.com", "other-client", Instant.now().plusSeconds(300), true, keys),
                credential("accounts.google.com", "web-client", Instant.now().minusSeconds(120), true, keys),
                credential("accounts.google.com", "web-client", Instant.now().plusSeconds(300), false, keys), "invalid")) {
            assertEquals(401, assertThrows(ResponseStatusException.class, () -> verifier.verify(token)).getStatusCode().value());
        }
    }
    @Test void rejectsWrongSignature() throws Exception {
        var other = KeyPairGenerator.getInstance("RSA"); other.initialize(2048);
        String token = credential("accounts.google.com", "web-client", Instant.now().plusSeconds(300), true, other.generateKeyPair());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> verifier.verify(token)).getStatusCode().value());
    }
    @Test void unconfiguredGoogleSignInDoesNotAcceptTokens() {
        var disabled = new GoogleTokenVerifier("", NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> disabled.verify("anything")).getStatusCode().value());
    }
    @Test void newGoogleUserNeedsProfileAndReceivesApplicationToken() {
        var users = mock(UserRepository.class);
        var tokens = mock(TokenService.class);
        var google = mock(GoogleTokenVerifier.class);
        when(google.verify("credential")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("subject", "alice@gmail.com"));
        when(tokens.issue(anyString())).thenReturn(new TokenService.TokenResponse("application-jwt", "Bearer", 1800));
        var service = new GoogleAuthService(google, users, tokens, mock(MongoTemplate.class));
        assertEquals("application-jwt", service.login("credential").accessToken());
        var captured = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(users).insert(captured.capture());
        assertNull(captured.getValue().passwordHash());
        assertNull(captured.getValue().profile());
        assertEquals("subject", captured.getValue().googleSubject());
        verify(tokens).issue(captured.getValue().id());
    }
    @Test void existingGoogleUserIsFoundBySubjectEvenWhenEmailChanges() {
        var users = mock(UserRepository.class);
        var google = mock(GoogleTokenVerifier.class);
        var tokens = mock(TokenService.class);
        when(google.verify("credential")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("subject", "new@gmail.com"));
        when(users.findByGoogleSubject("subject")).thenReturn(Optional.of(
                new UserAccount("existing", "old@gmail.com", null, null, Instant.now(), "subject")));
        new GoogleAuthService(google, users, tokens, mock(MongoTemplate.class)).login("credential");
        verify(tokens).issue("existing");
        verify(users, never()).insert(any(UserAccount.class));
        verify(users, never()).findByEmail(anyString());
    }
    @Test void matchingEmailNeverAutomaticallyLinksPasswordAccount() {
        var users = mock(UserRepository.class);
        var google = mock(GoogleTokenVerifier.class);
        var tokens = mock(TokenService.class);
        when(google.verify("credential")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("subject", "alice@gmail.com"));
        when(users.findByEmail("alice@gmail.com")).thenReturn(Optional.of(
                new UserAccount("existing", "alice@gmail.com", "password-hash", null, Instant.now())));
        var service = new GoogleAuthService(google, users, tokens, mock(MongoTemplate.class));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.login("credential")).getStatusCode().value());
        verifyNoInteractions(tokens);
        verify(users, never()).insert(any(UserAccount.class));
    }
    @Test void linkingRequiresSameEmailAndUpdatesOnlyIdentity() {
        var users = mock(UserRepository.class);
        var google = mock(GoogleTokenVerifier.class);
        var mongo = mock(MongoTemplate.class);
        when(users.findById("alice")).thenReturn(Optional.of(new UserAccount("alice", "alice@gmail.com", "hash", null, Instant.now())));
        when(google.verify("wrong")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("subject", "bob@gmail.com"));
        when(google.verify("right")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("subject", "alice@gmail.com"));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(UserAccount.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        var service = new GoogleAuthService(google, users, mock(TokenService.class), mongo);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.link("alice", "wrong")).getStatusCode().value());
        verifyNoInteractions(mongo);
        service.link("alice", "right");
        verify(mongo).updateFirst(argThat(q -> "alice".equals(q.getQueryObject().get("id"))),
                argThat(u -> ((org.bson.Document) u.getUpdateObject().get("$set")).keySet().equals(Set.of("googleSubject"))), eq(UserAccount.class));
    }
    @Test void duplicateGoogleSubjectCannotBeLinkedToSecondUser() {
        var users = mock(UserRepository.class);
        var google = mock(GoogleTokenVerifier.class);
        var mongo = mock(MongoTemplate.class);
        when(users.findById("alice")).thenReturn(Optional.of(new UserAccount("alice", "alice@gmail.com", "hash", null, Instant.now())));
        when(google.verify("credential")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("subject", "alice@gmail.com"));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(UserAccount.class))).thenThrow(new DuplicateKeyException("duplicate"));
        var service = new GoogleAuthService(google, users, mock(TokenService.class), mongo);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.link("alice", "credential")).getStatusCode().value());
    }
    @Test void incompleteProfileBlocksMatchingAndChatting() {
        var users = mock(UserRepository.class);
        when(users.findById("google-user")).thenReturn(Optional.of(new UserAccount("google-user", "alice@gmail.com", null, null, Instant.now(), "subject")));
        var service = new UserService(users, mock(MongoTemplate.class));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.getProfile("google-user")).getStatusCode().value());
    }
}
