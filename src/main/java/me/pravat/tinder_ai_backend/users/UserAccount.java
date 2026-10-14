package me.pravat.tinder_ai_backend.users;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import me.pravat.tinder_ai_backend.profile.Profile;

@Document("users")
public record UserAccount(String id, @Indexed(unique = true) String email,
                          @JsonIgnore String passwordHash, Profile profile, Instant createdAt) {
}
