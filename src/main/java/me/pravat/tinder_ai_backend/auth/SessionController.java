package me.pravat.tinder_ai_backend.auth;

import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class SessionController {
    private final RefreshSessionService sessions;
    public SessionController(RefreshSessionService sessions) { this.sessions = sessions; }
    @PostMapping("/refresh")
    public TokenService.TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return sessions.refresh(request, response);
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) { sessions.logout(request, response); }
}
