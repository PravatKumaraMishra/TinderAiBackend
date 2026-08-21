package me.pravat.tinder_ai_backend.matches;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matches")
@CrossOrigin(origins = "*")
public class MatchController {
    private final MatchService matchService;
    private final MatchRepository matchRepository;

    @PostMapping
    public Match createNewMatch(@RequestBody CreateMatchRequest request) {
        return matchService.createMatch(request.profileId());
    }

    @GetMapping
    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    public MatchController(MatchService matchService, MatchRepository matchRepository) {
        this.matchService = matchService;
        this.matchRepository = matchRepository;
    }

    public record CreateMatchRequest(String profileId) {
    }
}