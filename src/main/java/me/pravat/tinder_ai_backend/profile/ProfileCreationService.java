package me.pravat.tinder_ai_backend.profile;

import static me.pravat.tinder_ai_backend.Utils.selfieTypes;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

@Service
public class ProfileCreationService {
    private static final Logger log = LoggerFactory.getLogger(ProfileCreationService.class);

    private final ChatClient chatClient;
    private static final String PROFILES_FILE_PATH = "profiles.json";
    private final ProfileRepository profileRepository;
    private final PollinationsImageService imageService;
    private final me.pravat.tinder_ai_backend.storage.ImageStorageService images;

    public synchronized List<Profile> generateProfiles(int count, int minAge, int maxAge, List<String> ethnicities) {
        validateSettings(minAge, maxAge, ethnicities);
        if (count < 1 || count > 5) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "count must be between 1 and 5");
        }
        ProfileTools batchTools = new ProfileTools();
        List<Profile> created = new ArrayList<>();
        String batchId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        log.info("Profile generation started: batch={}, count={}", batchId, count);
        // Bound attempts when the provider fails to call the tool.
        for (int attempt = 0; attempt < count * 3 && created.size() < count; attempt++) {
            int previous = batchTools.getGeneratedProfiles().size();
            String stage = "persona generation";
            try {
                log.info("Profile generation: batch={}, attempt={}, stage={}", batchId, attempt + 1, stage);
                chatClient.prompt(buildProfilePrompt(getRandomElement(ethnicities),
                        ThreadLocalRandom.current().nextInt(minAge, maxAge + 1)))
                        .tools(batchTools).call().content();
                for (GenerateProfileResponse generated : batchTools.getGeneratedProfiles().subList(
                        previous, batchTools.getGeneratedProfiles().size())) {
                    if (created.size() == count) break;
                    stage = "image generation and upload";
                    Profile profile = generateProfileImage(generated, batchId);
                    stage = "database insert";
                    profileRepository.insert(profile);
                    created.add(profile);
                    log.info("Profile saved: batch={}, saved={}, requested={}", batchId, created.size(), count);
                }
                if (batchTools.getGeneratedProfiles().size() == previous) {
                    log.warn("AI returned without calling saveProfile: batch={}, attempt={}", batchId, attempt + 1);
                }
            } catch (Exception failure) {
                log.error("Profile generation failed: batch={}, stage={}, saved={}, elapsedMs={}",
                        batchId, stage, created.size(), (System.nanoTime() - started) / 1_000_000, failure);
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Profile generation failed; " + created.size() + " profiles already saved", failure);
            }
        }
        if (created.size() != count) {
            log.error("AI did not produce enough profiles: batch={}, saved={}, requested={}", batchId, created.size(), count);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "AI did not produce enough profiles; " + created.size() + " profiles already saved");
        }
        saveGeneratedBatch(created);
        log.info("Profile generation completed: batch={}, saved={}, elapsedMs={}",
                batchId, created.size(), (System.nanoTime() - started) / 1_000_000);
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

    private Profile generateProfileImage(GenerateProfileResponse profile, String batchId) {
        String uuid = UUID.randomUUID().toString();

        String prompt = getRandomElement(selfieTypes())
                + " profile picture of a "
                + profile.age() + " year old "
                + profile.ethnicity() + ", "
                + profile.gender() + ", "
                + profile.bio()
                + ", highly detailed, photorealistic, natural lighting.";

        log.info("Profile generation: batch={}, stage=image generation", batchId);
        PollinationsImageService.GeneratedImage generated = imageService.generate(prompt);
        log.info("Profile generation: batch={}, stage=image upload", batchId);
        String imageUrl = images.upload("ai-profiles/" + uuid, generated.bytes(), generated.extension());
        return new Profile(uuid, profile.firstName(), profile.lastName(), profile.age(),
                profile.ethnicity(), profile.gender(), profile.bio(), imageUrl,
                profile.myersBriggsPersonalityType());
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
            ProfileRepository profileRepository, PollinationsImageService imageService,
            me.pravat.tinder_ai_backend.storage.ImageStorageService images) {
        this.chatClient = chatClient;
        this.profileRepository = profileRepository;
        this.imageService = imageService;
        this.images = images;
    }
}
