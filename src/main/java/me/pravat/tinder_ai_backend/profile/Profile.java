package me.pravat.tinder_ai_backend.profile;

public record Profile(
                String id,
                String firstName,
                String lastName,
                int age,
                String ethnicity,
                Gender gender,
                String bio,
                String imageUrl,
                String myersBriggsPersonalityType) {
}