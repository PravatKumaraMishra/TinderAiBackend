package me.pravat.tinder_ai_backend;

import me.pravat.tinder_ai_backend.profile.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileGenerationTests {
    @Test void providerFailureIsLoggedWithStageAndOriginalCause() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ProfileCreationService.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            ChatClient ai = mock(ChatClient.class);
            var failure = new IllegalStateException("Provider unavailable");
            when(ai.prompt(anyString())).thenThrow(failure);
            var service = new ProfileCreationService(ai, mock(ProfileRepository.class),
                    mock(PollinationsImageService.class), mock(me.pravat.tinder_ai_backend.storage.ImageStorageService.class));
            var response = assertThrows(ResponseStatusException.class,
                    () -> service.generateProfiles(1, 25, 25, List.of("Indian")));
            assertEquals(502, response.getStatusCode().value());
            assertSame(failure, response.getCause());
            var error = events.list.stream()
                    .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR).findFirst().orElseThrow();
            assertTrue(error.getFormattedMessage().contains("stage=persona generation"));
            assertTrue(error.getFormattedMessage().contains("saved=0"));
            assertEquals("Provider unavailable", error.getThrowableProxy().getMessage());
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test void invalidSettingsAreRejectedBeforeProviderCalls() {
        ChatClient ai = mock(ChatClient.class);
        var service = new ProfileCreationService(ai, mock(ProfileRepository.class), mock(PollinationsImageService.class), mock(me.pravat.tinder_ai_backend.storage.ImageStorageService.class));
        for (int[] ages : List.of(new int[]{17, 25}, new int[]{30, 25}, new int[]{18, 121})) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.generateProfiles(1, ages[0], ages[1], List.of("Indian"))).getStatusCode().value());
        }
        assertThrows(ResponseStatusException.class, () -> service.generateProfiles(1, 18, 25, List.of()));
        assertThrows(ResponseStatusException.class, () -> service.generateProfiles(1, 18, 25, List.of(" ")));
        verifyNoInteractions(ai);
    }

    @Test void missingToolCallsAreBoundedAndEachRequestUsesFreshTools() {
        ChatClient ai = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ProfileRepository repository = mock(ProfileRepository.class);
        var service = new ProfileCreationService(ai, repository, mock(PollinationsImageService.class), mock(me.pravat.tinder_ai_backend.storage.ImageStorageService.class));
        var spec = ai.prompt("test");
        when(ai.prompt(org.mockito.ArgumentMatchers.anyString())).thenReturn(spec);
        for (int i = 0; i < 2; i++) {
            var error = assertThrows(ResponseStatusException.class, () -> service.generateProfiles(1, 25, 25, List.of("Indian")));
            assertEquals(502, error.getStatusCode().value());
        }
        var tools = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(spec, times(6)).tools(tools.capture());
        assertSame(tools.getAllValues().get(0), tools.getAllValues().get(2));
        assertNotSame(tools.getAllValues().get(0), tools.getAllValues().get(3));
        verifyNoInteractions(repository);
    }
}
