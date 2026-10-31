package me.pravat.tinder_ai_backend.users;

import jakarta.validation.constraints.*;
import me.pravat.tinder_ai_backend.profile.Gender;
import me.pravat.tinder_ai_backend.profile.Profile;

public record ProfileRequest(@NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName, @Min(18) @Max(120) int age,
        @NotBlank @Size(max = 100) String ethnicity, @NotNull Gender gender,
        @NotNull @Size(max = 2000) String bio, @Size(max = 600000) String imageUrl,
        @Size(max = 4) String myersBriggsPersonalityType) {
    @AssertTrue(message = "Profile photo must be an uploaded JPEG image or an existing image path")
    public boolean isPhotoValid() {
        if (imageUrl == null || imageUrl.isBlank()) return true;
        if (!imageUrl.startsWith("data:")) return imageUrl.length() <= 500;
        if (!imageUrl.startsWith("data:image/jpeg;base64,")) return false;
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(imageUrl.substring(23));
            if (bytes.length < 3 || bytes[0] != (byte) 0xff || bytes[1] != (byte) 0xd8) return false;
            try (var stream = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers = javax.imageio.ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) return false;
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    if (reader.getWidth(0) > 640 || reader.getHeight(0) > 640) return false;
                    return reader.read(0) != null;
                } finally {
                    reader.dispose();
                }
            }
        } catch (java.io.IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Profile toProfile(String id) {
        return new Profile(id, firstName.trim(), lastName.trim(), age, ethnicity.trim(), gender,
                bio, imageUrl, myersBriggsPersonalityType);
    }
}
