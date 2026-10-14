package me.pravat.tinder_ai_backend;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import me.pravat.tinder_ai_backend.auth.*;
import me.pravat.tinder_ai_backend.config.*;
import me.pravat.tinder_ai_backend.users.*;
import me.pravat.tinder_ai_backend.profile.*;
import me.pravat.tinder_ai_backend.matches.*;
import me.pravat.tinder_ai_backend.conversations.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(controllers = {AuthController.class, UserController.class, MatchController.class,
        ConversationController.class, ProfileController.class, GoogleAuthController.class}, properties = {
        "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "security.jwt.issuer=test-issuer", "security.jwt.audience=test-api",
        "security.jwt.access-token-ttl=30m", "security.cors.allowed-origins=http://localhost:3000"})
@Import({SecurityConfiguration.class, AuthService.class, TokenService.class, UserService.class,
        MatchService.class, ApiExceptionHandler.class})
class TinderAiBackendApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired JwtEncoder encoder;
    @MockitoBean UserRepository users;
    @MockitoBean MatchRepository matches;
    @MockitoBean ProfileRepository profiles;
    @MockitoBean ConversationRepository conversations;
    @MockitoBean ConversationService conversationService;
    @MockitoBean GoogleAuthService googleAuth;
    @MockitoBean org.springframework.data.mongodb.core.MongoTemplate mongo;
    private final Map<String, UserAccount> accounts = new HashMap<>();
    private static final String PROFILE = """
            {"firstName":"Alice","lastName":"Test","age":25,"ethnicity":"Indian",
             "gender":"FEMALE","bio":"Hello","imageUrl":"","myersBriggsPersonalityType":"INTJ"}
            """;

    @BeforeEach
    void setup() {
        accounts.clear();
        when(users.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            if (accounts.values().stream().anyMatch(a -> a.email().equals(account.email())))
                throw new org.springframework.dao.DuplicateKeyException("email");
            accounts.put(account.id(), account);
            return account;
        });
        when(users.findByEmail(anyString())).thenAnswer(i -> accounts.values().stream()
                .filter(a -> a.email().equals(i.getArgument(0))).findFirst());
        when(users.findById(anyString())).thenAnswer(i -> Optional.ofNullable(accounts.get(i.getArgument(0))));
        when(users.existsById(anyString())).thenAnswer(i -> accounts.containsKey(i.getArgument(0)));
        accounts.put("alice", new UserAccount("alice", "alice@example.com", "", profile("alice"), Instant.now()));
        accounts.put("bob", new UserAccount("bob", "bob@example.com", "", profile("bob"), Instant.now()));
    }
    private Profile profile(String id) {
        return new Profile(id, "Alice", "Test", 25, "Indian", Gender.FEMALE, "Hello", "", "INTJ");
    }
    private String bearer(String user) { return "Bearer " + tokens.issue(user).accessToken(); }

    @Test void registrationHashesPasswordAndLoginReturnsUsableJwt() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"NEW@EXAMPLE.COM\",\"password\":\"a-strong-password\",\"profile\":" + PROFILE + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accessToken").isString());
        UserAccount account = users.findByEmail("new@example.com").orElseThrow();
        assertNotEquals("a-strong-password", account.passwordHash());
        assertTrue(account.passwordHash().startsWith("{bcrypt}"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"NEW@example.com\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"));
        mvc.perform(get("/users/me").header("Authorization", bearer(account.id())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void duplicateRegistrationConflicts() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"ALICE@example.com\",\"password\":\"a-strong-password\",\"profile\":" + PROFILE + "}"))
                .andExpect(status().isConflict());
    }
    @Test void invalidRegistrationAndCredentialsAreRejected() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void protectedRoutesRequireAuthentication() throws Exception {
        for (String route : List.of("/matches", "/users/me", "/profiles/random", "/conversations/secret"))
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        mvc.perform(get("/matches").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
    @Test void expiredWrongAudienceAndDeletedAccountTokensAreRejected() throws Exception {
        Instant now = Instant.now();
        for (JwtClaimsSet claims : List.of(
                JwtClaimsSet.builder().issuer("test-issuer").subject("alice").audience(List.of("test-api"))
                        .issuedAt(now.minusSeconds(300)).expiresAt(now.minusSeconds(120)).build(),
                JwtClaimsSet.builder().issuer("test-issuer").subject("alice").audience(List.of("other-api"))
                        .issuedAt(now).expiresAt(now.plusSeconds(300)).build(),
                JwtClaimsSet.builder().issuer("wrong-issuer").subject("alice").audience(List.of("test-api"))
                        .issuedAt(now).expiresAt(now.plusSeconds(300)).build())) {
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
            mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/users/me").header("Authorization", bearer("deleted")))
                .andExpect(status().isUnauthorized());
    }
    @Test void tamperedTokenIsRejected() throws Exception {
        String[] parts = tokens.issue("alice").accessToken().split("\\.");
        char changed = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = changed + parts[2].substring(1);
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }
    @Test void wrongPasswordForRegisteredAccountIsRejected() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"new@example.com\",\"password\":\"a-strong-password\",\"profile\":" + PROFILE + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void matchListsAreScopedToEachUser() throws Exception {
        when(matches.findByUserId("alice")).thenReturn(List.of(new Match("a", "alice", profile("persona"), "ca")));
        when(matches.findByUserId("bob")).thenReturn(List.of());
        mvc.perform(get("/matches").header("Authorization", bearer("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("a"));
        mvc.perform(get("/matches").header("Authorization", bearer("bob")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(matches, never()).findAll();
    }
    @Test void bothUsersCanMatchTheSamePersona() throws Exception {
        when(profiles.findById("persona")).thenReturn(Optional.of(profile("persona")));
        when(matches.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));
        for (String user : List.of("alice", "bob")) {
            mvc.perform(post("/matches").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"profileId\":\"persona\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(user));
            verify(matches).existsByUserIdAndProfile_Id(user, "persona");
        }
    }
    @Test void cannotReadAnotherUsersConversation() throws Exception {
        when(conversations.findByIdAndUserId("ca", "alice")).thenReturn(Optional.of(
                new Conversation("ca", "alice", "persona", List.of(), 0L)));
        when(conversations.findByIdAndUserId("ca", "bob")).thenReturn(Optional.empty());
        mvc.perform(get("/conversations/ca").header("Authorization", bearer("alice"))).andExpect(status().isOk());
        mvc.perform(get("/conversations/ca").header("Authorization", bearer("bob"))).andExpect(status().isNotFound());
    }
    @Test void messageAuthorComesFromToken() throws Exception {
        mvc.perform(post("/conversations/ca").header("Authorization", bearer("bob"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"messageText\":\"Hello\",\"authorId\":\"alice\"}"))
                .andExpect(status().isOk());
        verify(conversationService).addMessage("bob", "ca", "Hello");
    }
    @Test void editingProfileUsesCurrentAccountAndNeverChangesCredentials() throws Exception {
        when(mongo.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(UserAccount.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1L, 1L, null));
        mvc.perform(put("/users/me/profile").header("Authorization", bearer("bob"))
                .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("bob"));
        verify(mongo).updateFirst(argThat(q -> q.getQueryObject().get("id").equals("bob")),
                argThat(u -> ((org.bson.Document) u.getUpdateObject().get("$set")).keySet().equals(Set.of("profile"))),
                eq(UserAccount.class));
        verify(users, never()).save(any(UserAccount.class));
    }
    @Test void googleLoginIsPublicAndLinkingRequiresAuthentication() throws Exception {
        when(googleAuth.login("google-credential")).thenReturn(tokens.issue("alice"));
        mvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"google-credential\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(post("/users/me/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"google-credential\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/users/me/google").header("Authorization", bearer("bob"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"google-credential\"}"))
                .andExpect(status().isNoContent());
        verify(googleAuth).link("bob", "google-credential");
    }
    @Test void corsAllowsConfiguredOriginOnly() throws Exception {
        mvc.perform(options("/matches").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/matches").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
    }
}
