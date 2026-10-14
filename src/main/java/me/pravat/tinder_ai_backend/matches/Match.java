package me.pravat.tinder_ai_backend.matches;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import me.pravat.tinder_ai_backend.profile.Profile;

@CompoundIndex(name = "user_profile_unique", def = "{'userId': 1, 'profile.id': 1}", unique = true,
        partialFilter = "{'userId': {$type: 'string'}}")
public record Match(String id, String userId, Profile profile, String conversationId) {
}
