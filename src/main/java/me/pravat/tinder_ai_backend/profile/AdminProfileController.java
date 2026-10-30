package me.pravat.tinder_ai_backend.profile;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/profiles")
public class AdminProfileController {
    private final ProfileCreationService service;

    private final int defaultMinAge;
    private final int defaultMaxAge;
    private final List<String> defaultEthnicities;

    public AdminProfileController(ProfileCreationService service,
            @Value("${tinderai.minAge}") int defaultMinAge,
            @Value("${tinderai.maxAge}") int defaultMaxAge,
            @Value("${tinderai.ethnicities}") List<String> defaultEthnicities) {
        ProfileCreationService.validateSettings(defaultMinAge, defaultMaxAge, defaultEthnicities);
        this.service = service;
        this.defaultMinAge = defaultMinAge;
        this.defaultMaxAge = defaultMaxAge;
        this.defaultEthnicities = List.copyOf(defaultEthnicities);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Profile> generate(@RequestBody GenerationRequest request) {
        if (request.count() == null || request.count() < 1 || request.count() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be between 1 and 5");
        }
        int minAge = request.minAge() == null ? defaultMinAge : request.minAge();
        int maxAge = request.maxAge() == null ? defaultMaxAge : request.maxAge();
        List<String> ethnicities = request.ethnicities() == null ? defaultEthnicities : request.ethnicities();
        ProfileCreationService.validateSettings(minAge, maxAge, ethnicities);
        return service.generateProfiles(request.count(), minAge, maxAge, ethnicities);
    }

    public record GenerationRequest(Integer count, Integer minAge, Integer maxAge, List<String> ethnicities) {}
}
