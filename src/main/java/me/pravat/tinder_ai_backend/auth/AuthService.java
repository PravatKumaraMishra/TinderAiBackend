package me.pravat.tinder_ai_backend.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.users.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final TokenService tokens;
    public AuthService(UserRepository users, PasswordEncoder passwords,
                       AuthenticationManager authentication, TokenService tokens) {
        this.users = users; this.passwords = passwords; this.authentication = authentication; this.tokens = tokens;
    }
    public TokenService.TokenResponse register(AuthController.RegisterRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        String id = UUID.randomUUID().toString();
        UserAccount account = new UserAccount(id, normalizeEmail(request.email()), passwords.encode(request.password()),
                request.profile().toProfile(id), Instant.now());
        try { users.insert(account); }
        catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        return tokens.issue(id);
    }
    public TokenService.TokenResponse login(AuthController.LoginRequest request) {
        var result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                normalizeEmail(request.email()), request.password()));
        return tokens.issue(result.getName());
    }
    public static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
