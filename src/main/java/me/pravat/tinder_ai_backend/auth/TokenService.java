package me.pravat.tinder_ai_backend.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder; this.properties = properties;
    }
    public TokenResponse issue(String userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(userId)
                .audience(List.of(properties.audience())).issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl())).id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds());
    }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
