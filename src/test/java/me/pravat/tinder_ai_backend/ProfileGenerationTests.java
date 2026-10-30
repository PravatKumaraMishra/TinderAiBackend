package me.pravat.tinder_ai_backend;

import me.pravat.tinder_ai_backend.profile.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileGenerationTests {
    @Test void missingToolCallsAreBoundedAndEachRequestUsesFreshTools() {
        ChatClient ai = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ProfileRepository repository = mock(ProfileRepository.class);
        ProfileTools startupTools = new ProfileTools();
        var service = new ProfileCreationService(ai, startupTools, repository, mock(RestClient.class));
        ReflectionTestUtils.setField(service, "ethnicities", List.of("Indian"));
        ReflectionTestUtils.setField(service, "minAge", 25);
        ReflectionTestUtils.setField(service, "maxAge", 25);
        ReflectionTestUtils.setField(service, "lookingForGender", "Female");
        ReflectionTestUtils.setField(service, "initializedProfiles", true);
        for (int i = 0; i < 2; i++) {
            var error = assertThrows(ResponseStatusException.class, () -> service.generateProfiles(1));
            assertEquals(502, error.getStatusCode().value());
        }
        var spec = ai.prompt("Create a Tinder profile persona for a Indian 25 year old female.\n"
                + "Include firstName, lastName, myersBriggsPersonalityType, bio.\n"
                + "After generating the profile, call the saveProfile tool.\n");
        var tools = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(spec, times(6)).tools(tools.capture());
        assertSame(tools.getAllValues().get(0), tools.getAllValues().get(2));
        assertNotSame(tools.getAllValues().get(0), tools.getAllValues().get(3));
        assertTrue(startupTools.getGeneratedProfiles().isEmpty());
        verifyNoInteractions(repository);
    }
}
