package me.pravat.tinder_ai_backend.profile;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ProfileTools {
    private final List<GenerateProfileResponse> generatedProfiles = new ArrayList<>();

    @Tool(description = "Save the profile information by providing firstName, lastName, bio, myersBriggsPersonalityType.")
    public String saveProfile(GenerateProfileResponse profile) {
        System.out.println("Tool called by LLM");
        System.out.println(profile);
        if (profile != null) {
            this.generatedProfiles.add(profile);
            return "Profile saved successfully.";
        }
        return "Profile was null and was not saved.";
    }

    public List<GenerateProfileResponse> getGeneratedProfiles() {
        return List.copyOf(generatedProfiles);
    }
}