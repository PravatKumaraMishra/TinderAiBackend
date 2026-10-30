package me.pravat.tinder_ai_backend.profile;

import static me.pravat.tinder_ai_backend.Utils.selfieTypes;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

@Service
public class ProfileCreationService {

    private final ChatClient chatClient;
    private static final String PROFILES_FILE_PATH = "profiles.json";
    private final ProfileRepository profileRepository;
    private final RestClient restClient;

    @Value("${pixazo.api-key}")
    private String pixazoApiKey;

    public synchronized List<Profile> generateProfiles(int count, int minAge, int maxAge, List<String> ethnicities) {
        validateSettings(minAge, maxAge, ethnicities);
        if (count < 1 || count > 5) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "count must be between 1 and 5");
        }
        ProfileTools batchTools = new ProfileTools();
        List<Profile> created = new ArrayList<>();
        // Bound attempts when the provider fails to call the tool.
        for (int attempt = 0; attempt < count * 3 && created.size() < count; attempt++) {
            int previous = batchTools.getGeneratedProfiles().size();
            try {
                chatClient.prompt(buildProfilePrompt(getRandomElement(ethnicities),
                        ThreadLocalRandom.current().nextInt(minAge, maxAge + 1)))
                        .tools(batchTools).call().content();
                for (GenerateProfileResponse generated : batchTools.getGeneratedProfiles().subList(
                        previous, batchTools.getGeneratedProfiles().size())) {
                    if (created.size() == count) break;
                    Profile profile = generateProfileImage(generated);
                    if (profile == null) {
                        throw new IllegalStateException("Image generation failed");
                    }
                    profileRepository.insert(profile);
                    created.add(profile);
                }
            } catch (Exception failure) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Profile generation failed; " + created.size() + " profiles already saved", failure);
            }
        }
        if (created.size() != count) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "AI did not produce enough profiles; " + created.size() + " profiles already saved");
        }
        saveGeneratedBatch(created);
        return List.copyOf(created);
    }

    private void saveGeneratedBatch(List<Profile> created) {
        List<Profile> all = new ArrayList<>(readExistingProfiles());
        all.addAll(created);
        writeProfiles(all);
    }

    private String buildProfilePrompt(String ethnicity, int age) {
        return """
                Create a Tinder profile persona for a %s %d year old %s.
                Include firstName, lastName, myersBriggsPersonalityType, bio.
                After generating the profile, call the saveProfile tool.
                """.formatted(ethnicity, age, ThreadLocalRandom.current().nextBoolean() ? "male" : "female");
    }

    private Profile generateProfileImage(GenerateProfileResponse profile) {
        String uuid = UUID.randomUUID().toString();

        String prompt = getRandomElement(selfieTypes())
                + " profile picture of a "
                + profile.age() + " year old "
                + profile.ethnicity() + ", "
                + profile.gender() + ", "
                + profile.bio()
                + ", highly detailed, photorealistic, natural lighting.";

        try {
            Map<?, ?> result = restClient.post()
                    .uri("https://gateway.pixazo.ai/flux-1-schnell/v1/getData")
                    .header("Ocp-Apim-Subscription-Key", pixazoApiKey)
                    .body(Map.of(
                            "prompt", prompt,
                            "num_steps", 4,
                            "width", 512,
                            "height", 512))
                    .retrieve()
                    .body(Map.class);

            String imageUrl = (String) result.get("output");
            byte[] image = restClient.get().uri(imageUrl).retrieve().body(byte[].class);
            Path dir = Paths.get("src/main/resources/static/images");
            Files.createDirectories(dir);

            String imageName = uuid + ".png";
            Files.write(dir.resolve(imageName), image);

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

        } catch (Exception e) {
            System.err.println("Image generation failed: " + e.getMessage());
            return null;
        }
    }

    private List<Profile> readExistingProfiles() {

        Path path = Paths.get(PROFILES_FILE_PATH);

        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        try (var reader = Files.newBufferedReader(path)) {

            Type listType = new TypeToken<ArrayList<Profile>>() {
            }.getType();

            List<Profile> profiles = new Gson().fromJson(reader, listType);
            return profiles != null ? profiles : new ArrayList<>();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read profiles.json", e);
        }
    }

    private void writeProfiles(List<Profile> profiles) {

        try (var writer = Files.newBufferedWriter(Paths.get(PROFILES_FILE_PATH))) {

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(profiles, writer);

        } catch (IOException e) {
            throw new RuntimeException("Failed to write profiles.json", e);
        }
    }

    public static void validateSettings(Integer minAge, Integer maxAge, List<String> ethnicities) {
        if (minAge == null || maxAge == null || minAge < 18 || maxAge > 120 || minAge > maxAge) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "ages must satisfy 18 <= minAge <= maxAge <= 120");
        }
        if (ethnicities == null || ethnicities.isEmpty()
                || ethnicities.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "ethnicities must contain nonblank values");
        }
    }

    private static <T> T getRandomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List argument cannot be null or empty");
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public ProfileCreationService(ChatClient chatClient,
            ProfileRepository profileRepository, RestClient restClient) {
        this.chatClient = chatClient;
        this.profileRepository = profileRepository;
        this.restClient = restClient;
    }
}
