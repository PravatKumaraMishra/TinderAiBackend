package me.pravat.tinder_ai_backend.auth;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.profile.Gender;

/** Reads Google profile data with an access token belonging to this OAuth client. */
@Component
public class GoogleProfileClient {
    private final String clientId;
    private final RestClient google;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleProfileClient(@Value("${security.google.client-id:}") String clientId) {
        this(clientId, RestClient.builder().requestFactory(factory()).build());
    }
    GoogleProfileClient(String clientId, RestClient google) { this.clientId = clientId; this.google = google; }
    private static org.springframework.http.client.JdkClientHttpRequestFactory factory() {
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }
    public GoogleTokenVerifier.GoogleIdentity fetch(String accessToken) {
        if (clientId.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured");
        try {
            TokenInfo info = google.get().uri("https://oauth2.googleapis.com/tokeninfo?access_token={token}", accessToken)
                    .retrieve().body(TokenInfo.class);
            if (info == null || !clientId.equals(info.aud()) || info.expires_in() <= 0)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google access token");
            UserInfo user = google.get().uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(UserInfo.class);
            if (user == null || user.sub() == null || user.sub().isBlank() || !user.email_verified() || user.email() == null)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
            int age = 0; Gender gender = null;
            try {
                Person person = google.get().uri("https://people.googleapis.com/v1/people/me?personFields=birthdays,genders")
                        .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Person.class);
                if (person != null) {
                    if (person.birthdays() != null) for (Birthday birthday : person.birthdays()) {
                        var date = birthday.date();
                        if (date == null || date.year() == 0 || date.month() == 0 || date.day() == 0) continue;
                        try { int years = Period.between(LocalDate.of(date.year(), date.month(), date.day()), LocalDate.now(ZoneOffset.UTC)).getYears();
                            if (years >= 18 && years <= 120) age = years;
                        } catch (DateTimeException ignored) { /* Incomplete Google birthdays remain editable. */ }
                        break;
                    }
                    if (person.genders() != null) for (GoogleGender value : person.genders()) {
                        gender = switch (value.value() == null ? "" : value.value()) {
                            case "male" -> Gender.MALE;
                            case "female" -> Gender.FEMALE;
                            case "non_binary" -> Gender.NON_BINARY;
                            default -> null;
                        };
                        if (gender != null) break;
                    }
                }
            } catch (org.springframework.web.client.RestClientException ignored) {
                // Missing permission or unavailable People API must not prevent basic sign-in.
            }
            return new GoogleTokenVerifier.GoogleIdentity(user.sub(), AuthService.normalizeEmail(user.email()),
                    user.given_name(), user.family_name(), user.picture(), age, gender);
        } catch (org.springframework.web.client.RestClientException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified");
        }
    }
    record TokenInfo(String aud, int expires_in) {}
    record UserInfo(String sub, String email, boolean email_verified, String given_name, String family_name, String picture) {}
    record Person(java.util.List<Birthday> birthdays, java.util.List<GoogleGender> genders) {}
    record Birthday(BirthDate date) {}
    record BirthDate(int year, int month, int day) {}
    record GoogleGender(String value) {}
}
