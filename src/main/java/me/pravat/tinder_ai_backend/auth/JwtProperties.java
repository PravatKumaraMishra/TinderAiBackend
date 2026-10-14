package me.pravat.tinder_ai_backend.auth;

import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @NotBlank String audience, @NotNull Duration accessTokenTtl) {
    public JwtProperties {
        if (accessTokenTtl != null && (accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || accessTokenTtl.compareTo(Duration.ofHours(1)) > 0)) {
            throw new IllegalArgumentException("JWT access-token-ttl must be positive and at most one hour");
        }
    }
}
