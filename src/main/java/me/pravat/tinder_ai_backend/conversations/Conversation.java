package me.pravat.tinder_ai_backend.conversations;

import java.util.List;
import org.springframework.data.annotation.Version;

public record Conversation(String id, String userId, String profileId,
                           List<ChatMessage> messages, @Version Long version) {
}
