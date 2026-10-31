package me.pravat.tinder_ai_backend.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import me.pravat.tinder_ai_backend.users.ProfileRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService service;
    private final RefreshSessionService sessions;
    public AuthController(AuthService service, RefreshSessionService sessions) { this.service = service; this.sessions = sessions; }
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenService.TokenResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        return sessions.start(service.register(request), httpRequest, response);
    }
    @PostMapping("/login")
    public TokenService.TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        return sessions.start(service.login(request), httpRequest, response);
    }
    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 64) String password, @NotNull @Valid ProfileRequest profile) {}
    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 64) String password) {}
}
