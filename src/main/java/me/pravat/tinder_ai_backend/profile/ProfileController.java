package me.pravat.tinder_ai_backend.profile;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProfileController {
    private final ProfileRepository profileRepository;

    @GetMapping("profiles/random")
    public Profile getRandomProfile() {
        Profile profile = profileRepository.getRandomProfile();
        if (profile == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No profiles available");
        return profile;
    }
    @GetMapping("profiles/{id}")
    public Profile getProfile(@PathVariable String id) {
        return profileRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }
    public ProfileController(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }
}
