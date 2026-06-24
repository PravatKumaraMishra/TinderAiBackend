package me.pravat.tinder_ai_backend;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import me.pravat.tinder_ai_backend.conversations.ChatMessage;
import me.pravat.tinder_ai_backend.conversations.Conversation;
import me.pravat.tinder_ai_backend.conversations.ConversationRepository;
import me.pravat.tinder_ai_backend.profile.Gender;
import me.pravat.tinder_ai_backend.profile.Profile;
import me.pravat.tinder_ai_backend.profile.ProfileRepository;

@SpringBootApplication
public class TinderAiBackendApplication implements CommandLineRunner {
	private final ConversationRepository conversationRepository;
	private final ProfileRepository profileRepository;
	private final ChatClient chatClient;

	public static void main(String[] args) {
		SpringApplication.run(TinderAiBackendApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		Profile profile = new Profile(
				"1",
				"Pravat",
				"Mishra",
				26,
				"Indian",
				Gender.MALE,
				"Software programmer",
				"pravat.jpg",
				"INTP");
		profileRepository.save(profile);

		Profile profile2 = new Profile(
				"2",
				"Sanu",
				"Vai",
				25,
				"Indian",
				Gender.MALE,
				"Backend Engineer",
				"sanu.jpg",
				"INTP");
		profileRepository.save(profile2);
		profileRepository.findAll().forEach(System.out::println);

		Conversation conversation = new Conversation(
				"1",
				profile.id(),
				List.of(
						new ChatMessage("Hello", profile.id(), LocalDateTime.now())));

		conversationRepository.save(conversation);
		conversationRepository.findAll().forEach(System.out::println);

		Prompt prompt = new Prompt("who is narendra modi");
		var response = chatClient.prompt(prompt).call().content();
		System.out.println("AI says: " + response);
	}

	public TinderAiBackendApplication(ProfileRepository profileRepository,
			ConversationRepository conversationRepository, ChatClient.Builder chatClientBuilder) {
		this.profileRepository = profileRepository;
		this.conversationRepository = conversationRepository;
		this.chatClient = chatClientBuilder.build();
	}
}
