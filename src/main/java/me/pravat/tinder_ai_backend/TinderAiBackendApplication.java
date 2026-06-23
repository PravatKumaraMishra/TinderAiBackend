package me.pravat.tinder_ai_backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import me.pravat.tinder_ai_backend.profile.Gender;
import me.pravat.tinder_ai_backend.profile.Profile;
import me.pravat.tinder_ai_backend.profile.ProfileRepository;

@SpringBootApplication
public class TinderAiBackendApplication implements CommandLineRunner {
	@Autowired
	private ProfileRepository profileRepository;

	public static void main(String[] args) {
		SpringApplication.run(TinderAiBackendApplication.class, args);
	}

	@Override
	public void run(String... args) {
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
		profileRepository.findAll().forEach(System.out::println);
	}

}
