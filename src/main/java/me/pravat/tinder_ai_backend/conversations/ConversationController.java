package me.pravat.tinder_ai_backend.conversations;

import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;

    @PostMapping("/{conversationId}")
    public Conversation addMessageToConversation(@PathVariable String conversationId,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SendMessageRequest request) {

        return conversationService.addMessage(jwt.getSubject(), conversationId, request.messageText());
    }

    @GetMapping("/{conversationId}")
    public Conversation getConversation(@PathVariable String conversationId, @AuthenticationPrincipal Jwt jwt) {

        return conversationRepository.findByIdAndUserId(conversationId, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find conversation with ID " + conversationId));
    }

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String messageText) {}

    public ConversationController(ConversationRepository conversationRepository,
            ConversationService conversationService) {

        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
    }
}