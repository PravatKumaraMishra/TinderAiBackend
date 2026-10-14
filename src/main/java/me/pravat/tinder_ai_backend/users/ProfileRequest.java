package me.pravat.tinder_ai_backend.users;

import jakarta.validation.constraints.*;
import me.pravat.tinder_ai_backend.profile.Gender;
import me.pravat.tinder_ai_backend.profile.Profile;

public record ProfileRequest(@NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName, @Min(18) @Max(120) int age,
        @NotBlank @Size(max = 100) String ethnicity, @NotNull Gender gender,
        @NotNull @Size(max = 2000) String bio, @Size(max = 500) String imageUrl,
        @Size(max = 4) String myersBriggsPersonalityType) {
    public Profile toProfile(String id) {
        return new Profile(id, firstName.trim(), lastName.trim(), age, ethnicity.trim(), gender,
                bio, imageUrl, myersBriggsPersonalityType);
    }
}
