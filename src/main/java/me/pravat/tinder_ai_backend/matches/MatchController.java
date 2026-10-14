package me.pravat.tinder_ai_backend.matches;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matches")
public class MatchController {
    private final MatchService matchService;
    private final MatchRepository matchRepository;

    @PostMapping
    public Match createNewMatch(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateMatchRequest request) {
        return matchService.createMatch(jwt.getSubject(), request.profileId());
    }

    @GetMapping
    public List<Match> getAllMatches(@AuthenticationPrincipal Jwt jwt) {
        return matchRepository.findByUserId(jwt.getSubject());
    }

    public MatchController(MatchService matchService, MatchRepository matchRepository) {
        this.matchService = matchService;
        this.matchRepository = matchRepository;
    }

    public record CreateMatchRequest(@NotBlank String profileId) {
    }
}