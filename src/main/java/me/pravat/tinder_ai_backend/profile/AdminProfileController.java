package me.pravat.tinder_ai_backend.profile;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/profiles")
public class AdminProfileController {
    private final ProfileCreationService service;

    public AdminProfileController(ProfileCreationService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Profile> generate(@RequestBody GenerationRequest request) {
        if (request.count() == null || request.count() < 1 || request.count() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be between 1 and 5");
        }
        return service.generateProfiles(request.count());
    }

    public record GenerationRequest(Integer count) {}
}
