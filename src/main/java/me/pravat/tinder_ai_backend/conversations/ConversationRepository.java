package me.pravat.tinder_ai_backend.conversations;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    Optional<Conversation> findByIdAndUserId(String id, String userId);
}
