package me.pravat.tinder_ai_backend.conversations;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/conversations")
@CrossOrigin(origins = "*")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;

    @PostMapping("/{conversationId}")
    public Conversation addMessageToConversation(@PathVariable String conversationId,
            @RequestBody ChatMessage chatMessage) {

        return conversationService.addMessage(conversationId, chatMessage);
    }

    @GetMapping("/{conversationId}")
    public Conversation getConversation(@PathVariable String conversationId) {

        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find conversation with ID " + conversationId));
    }

    public ConversationController(ConversationRepository conversationRepository,
            ConversationService conversationService) {

        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
    }
}