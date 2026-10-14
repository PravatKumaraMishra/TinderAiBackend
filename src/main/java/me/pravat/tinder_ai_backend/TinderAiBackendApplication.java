package me.pravat.tinder_ai_backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import me.pravat.tinder_ai_backend.profile.ProfileCreationService;

@SpringBootApplication
public class TinderAiBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TinderAiBackendApplication.class, args);
	}

    @Bean
    @ConditionalOnProperty(name = "startup-actions.seed-profiles", havingValue = "true")
    CommandLineRunner seedProfiles(ProfileCreationService profileCreationService) {
        return args -> {
            profileCreationService.createProfiles(1);
            profileCreationService.saveProfilesToDB();
        };
    }
}
