package me.pravat.tinder_ai_backend.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class GoogleAuthController {
    private final GoogleAuthService service;
    private final RefreshSessionService sessions;
    public GoogleAuthController(GoogleAuthService service, RefreshSessionService sessions) { this.service = service; this.sessions = sessions; }

    @org.springframework.beans.factory.annotation.Autowired
    private GoogleProfileClient googleProfiles;

    @PostMapping("/auth/google")
    public TokenService.TokenResponse login(@Valid @RequestBody CredentialRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        return sessions.start(request.accessToken() == null ? service.login(request.credential())
                : service.loginIdentity(googleProfiles.fetch(request.accessToken())), httpRequest, response);
    }
    @PostMapping("/users/me/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CredentialRequest request) {
        if (request.credential() == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "ID credential required for linking");
        service.link(jwt.getSubject(), request.credential());
    }
    public record CredentialRequest(@Size(max = 10000) String credential, @Size(max = 10000) String accessToken) {
        @AssertTrue(message = "Supply exactly one Google credential or access token")
        public boolean isValid() {
            return (credential != null && !credential.isBlank() && accessToken == null)
                    || (accessToken != null && !accessToken.isBlank() && credential == null);
        }
    }
}
