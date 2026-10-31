package me.pravat.tinder_ai_backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Uses Google's rotating public keys; these tokens are never accepted as application access tokens. */
@Component
public class GoogleTokenVerifier {
    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(@Value("${security.google.client-id:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build());
    }

    GoogleTokenVerifier(String clientId, NimbusJwtDecoder google) {
        this.clientId = clientId;
        google.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), jwt -> {
            String issuer = jwt.getClaimAsString("iss");
            boolean valid = ("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer))
                    && jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                    && jwt.getExpiresAt() != null && jwt.getSubject() != null && !jwt.getSubject().isBlank();
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        }));
        this.decoder = google;
    }

    public GoogleIdentity verify(String credential) {
        if (clientId.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Google sign-in is not configured");
        try {
            Jwt jwt = decoder.decode(credential);
            String email = jwt.getClaimAsString("email");
            if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) || email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
            }
            return new GoogleIdentity(jwt.getSubject(), AuthService.normalizeEmail(email), jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"), jwt.getClaimAsString("picture"));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google credential");
        }
    }
    public record GoogleIdentity(String subject, String email, String firstName, String lastName, String picture, int age, me.pravat.tinder_ai_backend.profile.Gender gender) {
        public GoogleIdentity(String subject, String email, String firstName, String lastName, String picture) { this(subject, email, firstName, lastName, picture, 0, null); }
        public GoogleIdentity(String subject, String email) { this(subject, email, null, null, null); }
        public me.pravat.tinder_ai_backend.profile.Profile profile(String id) {
            String photo = picture != null && picture.startsWith("https://") && picture.length() <= 500 ? picture : "";
            return new me.pravat.tinder_ai_backend.profile.Profile(id, firstName, lastName, age, "", gender, "", photo, "");
        }
    }
}
