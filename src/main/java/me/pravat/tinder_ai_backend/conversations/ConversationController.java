package me.pravat.tinder_ai_backend.conversations;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConversationController {

        private final ConversationRepository conversationRepository;

        @PostMapping("/conversations")
        public Conversation createNewConversation(@RequestBody CreateConversationRequest request) {
                Conversation conversation = new Conversation(
                                UUID.randomUUID().toString(),
                                request.profileId(),
                                new ArrayList<>());
                conversationRepository.save(conversation);
                return conversation;
        }

        public record CreateConversationRequest(
                        String profileId) {
        }

        public ConversationController(ConversationRepository conversationRepository) {
                this.conversationRepository = conversationRepository;
        }
}