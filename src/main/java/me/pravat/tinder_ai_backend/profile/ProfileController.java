package me.pravat.tinder_ai_backend.profile;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import me.pravat.tinder_ai_backend.users.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProfileController {
    private final ProfileRepository profileRepository;

    private final UserService userService;

    @GetMapping("profiles/random")
    public Profile getRandomProfile(@AuthenticationPrincipal Jwt jwt) {
        Gender gender = userService.getProfile(jwt.getSubject()).gender();
        if (gender == null || gender == Gender.NON_BINARY) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Opposite-gender discovery is only defined for male and female profiles");
        }
        Gender targetGender = gender == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        Profile profile = profileRepository.getRandomProfileByGender(targetGender);
        if (profile == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No opposite-gender profiles available");
        return profile;
    }
    @GetMapping("profiles/{id}")
    public Profile getProfile(@PathVariable String id) {
        return profileRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }
    public ProfileController(ProfileRepository profileRepository, UserService userService) {
        this.profileRepository = profileRepository;
        this.userService = userService;
    }
}
