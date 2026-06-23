package me.pravat.tinder_ai_backend.conversations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import me.pravat.tinder_ai_backend.profile.ProfileRepository;

@RestController
public class ConversationController {

        private final ConversationRepository conversationRepository;
        private final ProfileRepository profileRepository;

        @PostMapping("/conversations")
        public Conversation createNewConversation(@RequestBody CreateConversationRequest request) {
                Conversation conversation = new Conversation(
                                UUID.randomUUID().toString(),
                                request.profileId(),
                                new ArrayList<>());
                conversationRepository.save(conversation);
                return conversation;
        }

        @PostMapping("/conversations/{conversationId}")
        public Conversation addMessageToConversation(
                        @PathVariable String conversationId,
                        @RequestBody ChatMessage chatMessage) {
                Conversation conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unable to find conversation with the ID " + conversationId));
                profileRepository.findById(chatMessage.authorId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unable to find a profile with ID " + chatMessage.authorId()));

                ChatMessage messageWithTime = new ChatMessage(
                                chatMessage.messageText(),
                                chatMessage.authorId(),
                                LocalDateTime.now());
                conversation.messages().add(messageWithTime);
                conversationRepository.save(conversation);
                return conversation;
        }

        @GetMapping("/conversations/{conversationId}")
        public Conversation getConversation(
                        @PathVariable String conversationId) {
                return conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unable to find conversation with the ID " + conversationId));
        }

        public record CreateConversationRequest(
                        String profileId) {
        }

        public ConversationController(ConversationRepository conversationRepository,
                        ProfileRepository profileRepository) {
                this.conversationRepository = conversationRepository;
                this.profileRepository = profileRepository;
        }
}