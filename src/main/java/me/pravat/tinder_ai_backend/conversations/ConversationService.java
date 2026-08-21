package me.pravat.tinder_ai_backend.conversations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import me.pravat.tinder_ai_backend.profile.Profile;
import me.pravat.tinder_ai_backend.profile.ProfileRepository;

@Service
public class ConversationService {

    private final ChatClient chatClient;
    private final ProfileRepository profileRepository;
    private final ConversationRepository conversationRepository;

    public Conversation addMessage(String conversationId, ChatMessage chatMessage) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find conversation with ID " + conversationId));

        Profile profile = profileRepository.findById(conversation.profileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find profile with ID " + conversation.profileId()));

        Profile user = profileRepository.findById(chatMessage.authorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find profile with ID " + chatMessage.authorId()));

        conversation.messages()
                .add(new ChatMessage(chatMessage.messageText(), chatMessage.authorId(), LocalDateTime.now()));

        generateProfileResponse(conversation, profile, user);
        return conversationRepository.save(conversation);
    }

    public Conversation generateProfileResponse(Conversation conversation, Profile profile, Profile user) {

        Prompt prompt = buildPrompt(conversation, profile, user);
        String response = chatClient.prompt(prompt).call().content();
        conversation.messages().add(new ChatMessage(response, profile.id(), LocalDateTime.now()));
        return conversation;
    }

    private Prompt buildPrompt(Conversation conversation, Profile profile, Profile user) {

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(buildSystemMessage(profile, user)));

        messages.addAll(conversation.messages().stream()
                .map(message -> toAiMessage(message, profile.id())).toList());

        return new Prompt(messages);
    }

    private String buildSystemMessage(Profile profile, Profile user) {
        return """
                You are a %d year old %s %s called %s %s matched
                with a %d year old %s %s called %s %s on Tinder.

                This is an in-app text conversation between you two.

                Pretend to be the provided person and respond to the
                conversation as if writing on Tinder.

                Your bio is: %s
                Your Myers Briggs personality type is: %s

                Respond in the role of this person only.

                # Personality and Tone

                - Keep responses short and natural.
                - Be friendly, approachable, and slightly playful.
                - No hashtags or generic messages.
                - Reflect confidence and genuine interest.
                - Use humor and wit appropriately.
                - Match the tone of the user's messages.

                # Conversation

                - Avoid generic greetings.
                - Ask interesting questions.
                - Use information from the other person's profile.
                - Show genuine curiosity.
                - Ask open-ended questions when appropriate.
                - Use playful banter or light teasing.
                - Suggest fun activities when appropriate.

                # Respect

                - Be respectful and considerate.
                - Avoid controversial or sensitive topics unless initiated.
                - Respect personal boundaries.
                """.formatted(
                profile.age(),
                profile.ethnicity(),
                profile.gender(),
                profile.firstName(),
                profile.lastName(),

                user.age(),
                user.ethnicity(),
                user.gender(),
                user.firstName(),
                user.lastName(),

                profile.bio(),
                profile.myersBriggsPersonalityType());
    }

    private Message toAiMessage(ChatMessage message, String profileId) {

        if (message.authorId().equals(profileId))
            return new AssistantMessage(message.messageText());

        return new UserMessage(message.messageText());
    }

    public ConversationService(ChatClient chatClient, ProfileRepository profileRepository,
            ConversationRepository conversationRepository) {
        this.chatClient = chatClient;
        this.profileRepository = profileRepository;
        this.conversationRepository = conversationRepository;
    }
}