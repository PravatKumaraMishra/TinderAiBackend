package me.pravat.tinder_ai_backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class GoogleAuthController {
    private final GoogleAuthService service;
    public GoogleAuthController(GoogleAuthService service) { this.service = service; }

    @PostMapping("/auth/google")
    public TokenService.TokenResponse login(@Valid @RequestBody CredentialRequest request) {
        return service.login(request.credential());
    }
    @PostMapping("/users/me/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CredentialRequest request) {
        service.link(jwt.getSubject(), request.credential());
    }
    public record CredentialRequest(@NotBlank @Size(max = 10000) String credential) {}
}
