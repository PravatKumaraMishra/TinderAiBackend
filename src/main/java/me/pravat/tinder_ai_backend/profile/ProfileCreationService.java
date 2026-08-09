package me.pravat.tinder_ai_backend.profile;

import static me.pravat.tinder_ai_backend.Utils.selfieTypes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.micrometer.common.util.StringUtils;

@Service
public class ProfileCreationService {

    private final ChatClient chatClient;
    private final ProfileTools profileTools;
    private static final String PROFILES_FILE_PATH = "profiles.json";
    private final ProfileRepository profileRepository;

    @Value("${startup-actions.initializeProfiles:false}")
    private boolean initializedProfiles;

    @Value("${tinderai.lookingForGender}")
    private String lookingForGender;

    @Value("${tinderai.minAge}")
    private int minAge;

    @Value("${tinderai.maxAge}")
    private int maxAge;

    @Value("${tinderai.ethnicities}")
    private List<String> ethnicities;

    @Value("#{${tinderai.character.user}}")
    private Map<String, String> userProfileProperties;

    public ProfileCreationService(ChatClient chatClient, ProfileTools profileTools,
            ProfileRepository profileRepository) {
        this.chatClient = chatClient;
        this.profileTools = profileTools;
        this.profileRepository = profileRepository;
    }

    public void createProfiles(int numberOfProfiles) {
        if (initializedProfiles) {
            return;
        }

        Collections.shuffle(ethnicities);
        String gender = lookingForGender;

        for (String ethnicity : ethnicities) {
            int age = ThreadLocalRandom.current().nextInt(minAge, maxAge + 1);

            List<Profile> profiles = profileTools.getGeneratedProfiles();
            if (profiles.size() >= numberOfProfiles) {
                saveProfileToJson(profiles);
                return;
            }

            String buildPrompt = "Create a Tinder profile persona for a " + ethnicity + " " + age + " year old "
                    + gender.toLowerCase()
                    + " including first name, last name, Myers Briggs personality type, and a Tinder bio";

            Prompt prompt = new Prompt(buildPrompt);
            chatClient.prompt(prompt).tools(profileTools).call().content();
        }
    }

    private void saveProfileToJson(List<Profile> generatedProfiles) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            List<Profile> existingProfiles = new ArrayList<>();
            File file = new File(PROFILES_FILE_PATH);

            if (file.exists() && file.length() > 0) {
                try (FileReader reader = new FileReader(file)) {
                    Type listType = new TypeToken<ArrayList<Profile>>() {
                    }.getType();
                    List<Profile> data = gson.fromJson(reader, listType);
                    if (data != null) {
                        existingProfiles.addAll(data);
                    }
                }
            }

            List<Profile> finalProfilesToSave = new ArrayList<>();
            for (Profile profile : generatedProfiles) {
                if (profile.imageUrl() == null || profile.imageUrl().isBlank()) {
                    profile = generateProfileImage(profile);
                }
                if (profile != null) {
                    finalProfilesToSave.add(profile);
                }
            }

            finalProfilesToSave.addAll(existingProfiles);

            String jsonString = gson.toJson(finalProfilesToSave);
            try (FileWriter writer = new FileWriter(PROFILES_FILE_PATH)) {
                writer.write(jsonString);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to save profiles to JSON string", e);
        }
    }

    private Profile generateProfileImage(Profile profile) {
        String uuid = (profile.id() == null || StringUtils.isBlank(profile.id()))
                ? UUID.randomUUID().toString()
                : profile.id();

        // 1. Build a clean URL-safe prompt
        String randomSelfieType = getRandomElement(selfieTypes());
        String rawPrompt = randomSelfieType + " Profile picture of a " + profile.age() + " year old, "
                + profile.ethnicity() + ", " + profile.gender() + ", highly detailed, photorealistic. tinder bio: "
                + profile.bio();

        String encodedPrompt = URLEncoder.encode(rawPrompt, StandardCharsets.UTF_8);
        String imageUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt + "?width=512&height=512&nologo=true";

        System.out.println("Generating image for " + profile.firstName() + " via Pollinations.ai...");

        // 2. HTTP GET to fetch image bytes
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200 && response.body() != null) {

                String imageName = uuid + ".jpg";
                String directoryPath = "src/main/resources/static/images/";
                Path directory = Paths.get(directoryPath);

                if (!Files.exists(directory)) {
                    Files.createDirectories(directory);
                }

                // Write payload out to file directly
                Files.write(directory.resolve(imageName), response.body());
                System.out.println("Successfully saved image for " + profile.firstName() + "!");

                // Re-instantiate immutable record with valid URL pointer
                return new Profile(
                        uuid,
                        profile.firstName(),
                        profile.lastName(),
                        profile.age(),
                        profile.ethnicity(),
                        profile.gender(),
                        profile.bio(),
                        imageName,
                        profile.myersBriggsPersonalityType());
            } else {
                System.err.println("Pollinations returned error code: " + response.statusCode());
                return profile; // Return profile intact even if image fails
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch image: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore interrupted state
            return profile;
        }
    }

    @Transactional
    public void saveProfilesToDB() {
        Gson gson = new Gson();
        try {
            List<Profile> existingProfiles = gson.fromJson(
                    new FileReader(PROFILES_FILE_PATH),
                    new TypeToken<ArrayList<Profile>>() {
                    }.getType());
            profileRepository.deleteAll();
            profileRepository.saveAll(existingProfiles);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Profile profile = new Profile(
                userProfileProperties.get("id"),
                userProfileProperties.get("firstName"),
                userProfileProperties.get("lastName"),
                Integer.parseInt(userProfileProperties.get("age")),
                userProfileProperties.get("ethnicity"),
                Gender.valueOf(userProfileProperties.get("gender")),
                userProfileProperties.get("bio"),
                userProfileProperties.get("imageUrl"),
                userProfileProperties.get("myersBriggsPersonalityType"));
        var savedProfile = profileRepository.save(profile);
        System.out.println(savedProfile);
    }

    private static <T> T getRandomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List argument cannot be null or empty");
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
