package me.pravat.tinder_ai_backend.profile;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PollinationsImageService {
    private static final String BASE_URL = "https://gen.pollinations.ai";
    private final RestClient client;
    private final String apiKey;
    private final String model;

    public PollinationsImageService(RestClient client,
            @Value("${pollinations.api-key:}") String apiKey,
            @Value("${pollinations.image-model:community/scriptsnsenses-sys/flux-2-dev-free}") String model) {
        this.client = client;
        this.apiKey = apiKey;
        this.model = model;
    }

    public GeneratedImage generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("POLLINATIONS_API_KEY must be configured for image generation");
        }
        try {
            requireFreeModel();
            var uri = UriComponentsBuilder.fromUriString(BASE_URL)
                    .path("/image/{prompt}").queryParam("model", "{model}")
                    .queryParam("width", 512).queryParam("height", 512)
                    .encode().buildAndExpand(Map.of("prompt", prompt, "model", model)).toUri();
            var response = client.get().uri(uri)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .retrieve().toEntity(byte[].class);
            MediaType type = response.getHeaders().getContentType();
            String extension = type == null ? null : switch (type.getType() + "/" + type.getSubtype()) {
                case "image/png" -> ".png";
                case "image/jpeg" -> ".jpg";
                case "image/webp" -> ".webp";
                default -> null;
            };
            if (extension == null || response.getBody() == null || response.getBody().length == 0) {
                throw new IllegalStateException("Pollinations returned no supported image");
            }
            return new GeneratedImage(response.getBody(), extension);
        } catch (RestClientResponseException failure) {
            throw new IllegalStateException("Pollinations image request failed (HTTP "
                    + failure.getStatusCode().value() + ")", failure);
        }
    }

    private void requireFreeModel() {
        String body = client.get().uri(BASE_URL + "/image/models")
                .retrieve().body(String.class);
        Model[] models = new Gson().fromJson(body, Model[].class);
        if (models != null) {
            for (Model candidate : models) {
                if (!model.equals(candidate.name())) continue;
                if (!Boolean.FALSE.equals(candidate.paidOnly()) || candidate.pricing() == null
                        || !candidate.pricing().containsKey("currency")) break;
                for (var price : candidate.pricing().entrySet()) {
                    if (price.getKey().equals("currency")) continue;
                    // Unknown or malformed rates also fail closed.
                    try {
                        if (new BigDecimal(price.getValue()).signum() != 0) {
                            throw new IllegalStateException("Configured Pollinations model is not free");
                        }
                    } catch (NumberFormatException failure) {
                        throw new IllegalStateException("Cannot verify Pollinations model pricing", failure);
                    }
                }
                return;
            }
        }
        throw new IllegalStateException("Configured free Pollinations model is unavailable or pricing cannot be verified");
    }

    public record GeneratedImage(byte[] bytes, String extension) {}
    private record Model(String name, @SerializedName("paid_only") Boolean paidOnly, Map<String, String> pricing) {}
}
