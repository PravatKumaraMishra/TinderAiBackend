package me.pravat.tinder_ai_backend.matches;

import me.pravat.tinder_ai_backend.profile.Profile;

public record Match(String id, Profile profile, String conversationId) {
}