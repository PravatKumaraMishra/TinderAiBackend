package me.pravat.tinder_ai_backend.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.users.*;

@Service
public class GoogleAuthService {
    private final GoogleTokenVerifier verifier;
    private final UserRepository users;
    private final TokenService tokens;
    private final MongoTemplate mongo;

    public GoogleAuthService(GoogleTokenVerifier verifier, UserRepository users, TokenService tokens, MongoTemplate mongo) {
        this.verifier = verifier; this.users = users; this.tokens = tokens; this.mongo = mongo;
    }

    public TokenService.TokenResponse login(String credential) {
        var identity = verifier.verify(credential);
        var existing = users.findByGoogleSubject(identity.subject());
        if (existing.isPresent()) return tokens.issue(existing.get().id());
        if (users.findByEmail(identity.email()).isPresent()) throw linkingRequired();
        UserAccount account = new UserAccount(UUID.randomUUID().toString(), identity.email(), null,
                null, Instant.now(), identity.subject());
        try {
            users.insert(account);
        } catch (DuplicateKeyException ex) {
            // A concurrent first sign-in may already have created this exact Google account.
            return users.findByGoogleSubject(identity.subject()).map(user -> tokens.issue(user.id()))
                    .orElseThrow(GoogleAuthService::linkingRequired);
        }
        return tokens.issue(account.id());
    }

    public void link(String userId, String credential) {
        var identity = verifier.verify(credential);
        UserAccount account = users.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        if (!account.email().equals(identity.email())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Choose the Google account with the same email as your application account");
        if (identity.subject().equals(account.googleSubject())) return;
        if (account.googleSubject() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A different Google account is already linked");
        try {
            // Only update the identity field; do not overwrite concurrent profile edits.
            var result = mongo.updateFirst(Query.query(Criteria.where("id").is(userId).and("googleSubject").is(null)),
                    new Update().set("googleSubject", identity.subject()), UserAccount.class);
            if (result.getModifiedCount() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account changed; reload and try again");
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Google account is already linked to another user");
        }
    }

    private static ResponseStatusException linkingRequired() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Sign in with your password, then link Google from your account page");
    }
}
