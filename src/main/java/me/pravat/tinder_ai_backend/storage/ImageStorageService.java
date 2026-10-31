package me.pravat.tinder_ai_backend.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ImageStorageService {
    private final String endpoint, accessKey, secretKey, bucket, publicUrl;

    public ImageStorageService(@Value("${storage.r2.endpoint:}") String endpoint,
            @Value("${storage.r2.access-key:}") String accessKey,
            @Value("${storage.r2.secret-key:}") String secretKey,
            @Value("${storage.r2.bucket:}") String bucket,
            @Value("${storage.r2.public-url:}") String publicUrl) {
        this.endpoint = endpoint; this.accessKey = accessKey; this.secretKey = secretKey;
        this.bucket = bucket; this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    public String userPhoto(String userId, String incoming, String existing) {
        if (incoming == null || incoming.isBlank()) return incoming;
        if (!incoming.startsWith("data:")) {
            // Only retain the account's current photo; never accept another account's object URL.
            if (incoming.equals(existing)) return incoming;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a new profile photo");
        }
        if (!incoming.startsWith("data:image/jpeg;base64,")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile photo must be JPEG");
        }
        byte[] bytes = Base64.getDecoder().decode(incoming.substring(23));
        if (bytes.length > 500 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile photo must be up to 500 KB");
        return upload("users/" + userId, bytes, ".jpg");
    }

    public String upload(String prefix, byte[] bytes, String extension) {
        if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank()
                || bucket.isBlank() || publicUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        }
        String contentType = switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            default -> throw new IllegalArgumentException("Unsupported image format");
        };
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be between 1 byte and 10 MB");
        }
        String key = prefix + "/" + UUID.randomUUID() + extension;
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint)).region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false).build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(45)).apiCallAttemptTimeout(Duration.ofSeconds(20)).build())
                .build()) {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                    .cacheControl("public, max-age=31536000, immutable").build(), RequestBody.fromBytes(bytes));
            return publicUrl + "/" + key;
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image upload failed", failure);
        }
    }
}
