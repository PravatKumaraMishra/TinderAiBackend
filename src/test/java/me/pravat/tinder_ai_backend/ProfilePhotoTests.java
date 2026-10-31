package me.pravat.tinder_ai_backend;

import me.pravat.tinder_ai_backend.users.ProfileRequest;
import me.pravat.tinder_ai_backend.profile.Gender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfilePhotoTests {
    private ProfileRequest profile(String image) {
        return new ProfileRequest("Test", "User", 25, "Indian", Gender.MALE, "Hello", image, "INFP");
    }

    @Test void acceptsUploadedJpegAndExistingImages() throws Exception {
        var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpeg", buffer);
        assertTrue(profile("data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(buffer.toByteArray())).isPhotoValid());
        assertTrue(profile("existing.jpg").isPhotoValid());
        assertTrue(profile(null).isPhotoValid());
    }

    @Test void rejectsPhotosAbove500Kb() throws Exception {
        var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpeg", buffer);
        byte[] oversized = java.util.Arrays.copyOf(buffer.toByteArray(), 500 * 1024 + 1);
        assertFalse(profile("data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(oversized)).isPhotoValid());
    }

    @Test void rejectsMalformedAndOversizedPhotos() throws Exception {
        assertFalse(profile("data:image/svg+xml;base64,PHN2Zz4=").isPhotoValid());
        assertFalse(profile("data:image/jpeg;base64,invalid!").isPhotoValid());
        var image = new java.awt.image.BufferedImage(641, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpeg", buffer);
        assertFalse(profile("data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(buffer.toByteArray())).isPhotoValid());
    }
}
