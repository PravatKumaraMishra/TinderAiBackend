package me.pravat.tinder_ai_backend.profile;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

    private final ProfileRepository profileRepository;

    @CrossOrigin(origins = "*")
    @GetMapping("profiles/random")
    public Profile getRandomProfile() {
        return profileRepository.getRandomProfile();
    }

    ProfileController(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }
}
