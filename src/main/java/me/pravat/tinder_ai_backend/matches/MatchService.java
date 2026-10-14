package me.pravat.tinder_ai_backend.matches;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import me.pravat.tinder_ai_backend.conversations.Conversation;
import me.pravat.tinder_ai_backend.conversations.ConversationRepository;
import me.pravat.tinder_ai_backend.profile.Profile;
import me.pravat.tinder_ai_backend.profile.ProfileRepository;
import me.pravat.tinder_ai_backend.users.UserService;

@Service
public class MatchService {
    private final MatchRepository matchRepository;
    private final ProfileRepository profileRepository;
    private final ConversationRepository conversationRepository;
    private final UserService users;

    public Match createMatch(String userId, String profileId) {
        users.getProfile(userId);
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find a profile with ID " + profileId));

        if (matchRepository.existsByUserIdAndProfile_Id(userId, profileId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A match already exists with profile ID " + profileId);

        Conversation conversation = new Conversation(UUID.randomUUID().toString(), userId, profile.id(), new ArrayList<>(), null);
        conversationRepository.save(conversation);
        Match match = new Match(UUID.randomUUID().toString(), userId, profile, conversation.id());
        try {
            return matchRepository.save(match);
        } catch (DuplicateKeyException ex) {
            conversationRepository.deleteById(conversation.id());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A match already exists with this profile");
        } catch (RuntimeException ex) {
            conversationRepository.deleteById(conversation.id());
            throw ex;
        }
    }

    public MatchService(MatchRepository matchRepository, ProfileRepository profileRepository,
            ConversationRepository conversationRepository, UserService users) {
        this.matchRepository = matchRepository;
        this.profileRepository = profileRepository;
        this.conversationRepository = conversationRepository;
        this.users = users;
    }
}
