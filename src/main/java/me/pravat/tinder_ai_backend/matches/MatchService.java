package me.pravat.tinder_ai_backend.matches;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import me.pravat.tinder_ai_backend.conversations.Conversation;
import me.pravat.tinder_ai_backend.conversations.ConversationRepository;
import me.pravat.tinder_ai_backend.profile.Profile;
import me.pravat.tinder_ai_backend.profile.ProfileRepository;

@Service
public class MatchService {
    private final MatchRepository matchRepository;
    private final ProfileRepository profileRepository;
    private final ConversationRepository conversationRepository;

    public Match createMatch(String profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find a profile with ID " + profileId));

        Match existingMatch = matchRepository.findByProfile_Id(profileId).orElse(null);

        if (existingMatch != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A match already exists with profile ID " + profileId);

        Conversation conversation = new Conversation(UUID.randomUUID().toString(), profile.id(), new ArrayList<>());
        conversationRepository.save(conversation);
        Match match = new Match(UUID.randomUUID().toString(), profile, conversation.id());
        return matchRepository.save(match);
    }

    public MatchService(MatchRepository matchRepository, ProfileRepository profileRepository,
            ConversationRepository conversationRepository) {
        this.matchRepository = matchRepository;
        this.profileRepository = profileRepository;
        this.conversationRepository = conversationRepository;
    }
}