package me.pravat.tinder_ai_backend.conversations;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import me.pravat.tinder_ai_backend.profile.Profile;
import me.pravat.tinder_ai_backend.profile.ProfileRepository;

@RestController
public class ConversationController {

        private final ConversationRepository conversationRepository;
        private final ConversationService conversationService;
        private final ProfileRepository profileRepository;

        @CrossOrigin(origins = "*")
        @PostMapping("/conversations/{conversationId}")
        public Conversation addMessageToConversation(
                        @PathVariable String conversationId,
                        @RequestBody ChatMessage chatMessage) {
                Conversation conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unable to find conversation with the ID " + conversationId));
                String matchProfileId = conversation.profileId();

                Profile profile = profileRepository.findById(matchProfileId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unable to find a profile with ID " + matchProfileId));
                Profile user = profileRepository.findById(chatMessage.authorId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unable to find a profile with ID " + chatMessage.authorId()));

                ChatMessage messageWithTime = new ChatMessage(
                                chatMessage.messageText(),
                                chatMessage.authorId(),
                                LocalDateTime.now());
                conversation.messages().add(messageWithTime);
                conversationService.generateProfileResponse(conversation, profile, user);
                conversationRepository.save(conversation);
                return conversation;
        }

        @CrossOrigin(origins = "*")
        @GetMapping("/conversations/{conversationId}")
        public Conversation getConversation(
                        @PathVariable String conversationId) {
                return conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unable to find conversation with the ID " + conversationId));
        }

        public ConversationController(ConversationRepository conversationRepository,
                        ProfileRepository profileRepository, ConversationService conversationService) {
                this.conversationRepository = conversationRepository;
                this.profileRepository = profileRepository;
                this.conversationService = conversationService;
        }
}