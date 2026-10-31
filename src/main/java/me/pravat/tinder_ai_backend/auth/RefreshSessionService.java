package me.pravat.tinder_ai_backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.users.UserRepository;

@Service
public class RefreshSessionService {
    static final String COOKIE = "tinder_refresh";
    private final MongoTemplate mongo;
    private final TokenService tokens;
    private final JwtDecoder decoder;
    private final UserRepository users;
    private final Duration ttl;
    private final boolean secure;
    private final String sameSite;
    private final SecureRandom random = new SecureRandom();

    public RefreshSessionService(MongoTemplate mongo, TokenService tokens, JwtDecoder decoder, UserRepository users,
            @Value("${security.refresh.ttl:7d}") Duration ttl,
            @Value("${security.refresh.cookie-secure:true}") boolean secure,
            @Value("${security.refresh.same-site:Lax}") String sameSite) {
        this.mongo = mongo; this.tokens = tokens; this.decoder = decoder; this.users = users;
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Refresh TTL must be positive");
        if (!List.of("Strict", "Lax", "None").contains(sameSite) || (sameSite.equals("None") && !secure))
            throw new IllegalArgumentException("Invalid refresh cookie SameSite/Secure settings");
        this.ttl = ttl; this.secure = secure; this.sameSite = sameSite;
    }

    public TokenService.TokenResponse start(TokenService.TokenResponse token, HttpServletRequest request, HttpServletResponse response) {
        logout(request, response);
        String secret = secret();
        var session = new RefreshSession(UUID.randomUUID().toString(), decoder.decode(token.accessToken()).getSubject(),
                hash(secret), List.of(), Instant.now().plus(ttl));
        mongo.insert(session);
        cookie(response, session.id() + "." + secret, ttl);
        return token;
    }

    public TokenService.TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String value = value(request);
        if (value == null || !value.contains(".")) throw unauthorized(response);
        String id = value.substring(0, value.indexOf('.'));
        String oldHash = hash(value.substring(value.indexOf('.') + 1));
        String next = secret();
        var query = Query.query(Criteria.where("_id").is(id).and("currentHash").is(oldHash)
                .and("expiresAt").gt(Instant.now()));
        var session = mongo.findAndModify(query, new Update().set("currentHash", hash(next)).push("usedHashes", oldHash), RefreshSession.class);
        if (session == null) {
            // Only a previously valid secret can revoke a family; arbitrary guesses cannot.
            mongo.remove(Query.query(Criteria.where("_id").is(id).and("usedHashes").is(oldHash)), RefreshSession.class);
            throw unauthorized(response);
        }
        if (!users.existsById(session.userId())) {
            mongo.remove(Query.query(Criteria.where("_id").is(id)), RefreshSession.class);
            throw unauthorized(response);
        }
        cookie(response, id + "." + next, Duration.between(Instant.now(), session.expiresAt()));
        return tokens.issue(session.userId());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String value = value(request);
        if (value != null && value.contains(".")) {
            String id = value.substring(0, value.indexOf('.'));
            String digest = hash(value.substring(value.indexOf('.') + 1));
            mongo.remove(Query.query(new Criteria().andOperator(Criteria.where("_id").is(id),
                    new Criteria().orOperator(Criteria.where("currentHash").is(digest), Criteria.where("usedHashes").is(digest)))), RefreshSession.class);
        }
        cookie(response, "", Duration.ZERO);
    }
    private ResponseStatusException unauthorized(HttpServletResponse response) {
        cookie(response, "", Duration.ZERO);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
    }
    private String value(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies())
            if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    private void cookie(HttpServletResponse response, String value, Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value).httpOnly(true)
                .secure(secure).sameSite(sameSite).path("/auth").maxAge(age).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Document("refresh_sessions")
    public record RefreshSession(@Id String id, String userId, String currentHash, List<String> usedHashes,
            @org.springframework.data.mongodb.core.index.Indexed(expireAfter = "0s") Instant expiresAt) {}
}
