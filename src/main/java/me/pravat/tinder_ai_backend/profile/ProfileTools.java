package me.pravat.tinder_ai_backend.profile;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ProfileTools {
    List<Profile> generatedProfiles = new ArrayList<>();

    @Tool(description = "Save the profile information by providing only firstName, lastName, age, ethnicity, gender, bio, myersBriggsPersonalityType. Id and imageUrl should be null")
    public List<Profile> saveProfile(Profile profile) {
        System.out.println("Proof it is called by openai");
        System.out.println(profile);
        if (profile != null) {
            this.generatedProfiles.add(profile);
        }
        return generatedProfiles;
    }

    public List<Profile> getGeneratedProfiles() {
        return generatedProfiles;
    }
}