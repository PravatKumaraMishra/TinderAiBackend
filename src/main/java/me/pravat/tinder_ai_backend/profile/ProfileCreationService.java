package me.pravat.tinder_ai_backend.profile;

import static me.pravat.tinder_ai_backend.Utils.selfieTypes;

import java.io.FileNotFoundException;
import java.io.FileReader;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

@Service
public class ProfileCreationService {

    private final ChatClient chatClient;
    private final ProfileTools profileTools;
    private static final String PROFILES_FILE_PATH = "profiles.json";
    private final ProfileRepository profileRepository;
    private final RestClient restClient;

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

    @Value("${pixazo.api-key}")
    private String pixazoApiKey;

    public void createProfiles(int numberOfProfiles) {
        if (initializedProfiles) {
            return;
        }

        while (profileTools.getGeneratedProfiles().size() < numberOfProfiles) {
            String ethnicity = getRandomElement(ethnicities);
            int age = ThreadLocalRandom.current().nextInt(minAge, maxAge + 1);
            generateProfile(ethnicity, age);
        }
        saveProfileToJson(profileTools.getGeneratedProfiles());
    }

    private void generateProfile(String ethnicity, int age) {
        String prompt = buildProfilePrompt(ethnicity, age);
        chatClient.prompt(prompt).tools(profileTools).call().content();
    }

    private String buildProfilePrompt(String ethnicity, int age) {
        return """
                Create a Tinder profile persona for a %s %d year old %s.
                Include firstName, lastName, myersBriggsPersonalityType, bio.
                After generating the profile, call the saveProfile tool.
                """.formatted(ethnicity, age, lookingForGender.toLowerCase());
    }

    private void saveProfileToJson(List<GenerateProfileResponse> generatedProfiles) {
        List<Profile> existingProfiles = readExistingProfiles();

        List<Profile> profiles = generatedProfiles.stream().map((profile) -> generateProfileImage(profile))
                .toList();

        List<Profile> allProfiles = new ArrayList<>();
        allProfiles.addAll(profiles);
        allProfiles.addAll(existingProfiles);
        writeProfiles(allProfiles);
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

    @Transactional
    public void saveProfilesToDB() {
        Gson gson = new Gson();
        try {
            List<Profile> existingProfiles = gson.fromJson(new FileReader(PROFILES_FILE_PATH),
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

    public ProfileCreationService(ChatClient chatClient, ProfileTools profileTools,
            ProfileRepository profileRepository, RestClient restClient) {
        this.chatClient = chatClient;
        this.profileTools = profileTools;
        this.profileRepository = profileRepository;
        this.restClient = restClient;
    }
}
