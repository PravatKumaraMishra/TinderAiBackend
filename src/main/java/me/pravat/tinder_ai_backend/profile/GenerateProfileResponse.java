package me.pravat.tinder_ai_backend.profile;

public record GenerateProfileResponse(
        String firstName,
        String lastName,
        int age,
        String ethnicity,
        Gender gender,
        String bio,
        String myersBriggsPersonalityType) {
}