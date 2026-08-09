package me.pravat.tinder_ai_backend;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import me.pravat.tinder_ai_backend.profile.ProfileCreationService;

@SpringBootApplication
public class TinderAiBackendApplication implements CommandLineRunner {

	private final ProfileCreationService profileCreationService;

	public TinderAiBackendApplication(ProfileCreationService profileCreationService, ChatClient chatClient) {
		this.profileCreationService = profileCreationService;
	}

	public static void main(String[] args) {
		SpringApplication.run(TinderAiBackendApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		profileCreationService.createProfiles(1);
		profileCreationService.saveProfilesToDB();
	}
}
