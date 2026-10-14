package me.pravat.tinder_ai_backend.users;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import me.pravat.tinder_ai_backend.profile.Profile;

@RestController
@RequestMapping("/users/me")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        UserAccount account = service.get(jwt.getSubject());
        return new UserResponse(account.id(), account.email(), account.profile(), account.googleSubject() != null);
    }
    @PutMapping("/profile")
    public Profile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return service.updateProfile(jwt.getSubject(), request);
    }
    public record UserResponse(String id, String email, Profile profile, boolean googleLinked) {}
}
