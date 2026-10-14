package me.pravat.tinder_ai_backend;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.conversations.*;
import me.pravat.tinder_ai_backend.matches.*;
import me.pravat.tinder_ai_backend.profile.*;
import me.pravat.tinder_ai_backend.users.*;
import me.pravat.tinder_ai_backend.config.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserIsolationTests {
    @Test void unauthorizedSendNeverCallsAiOrWritesData() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        UserService users = mock(UserService.class);
        ChatClient ai = mock(ChatClient.class);
        when(conversations.findByIdAndUserId("alice-conversation", "bob")).thenReturn(Optional.empty());
        var service = new ConversationService(ai, profiles, conversations, users);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addMessage("bob", "alice-conversation", "Hello"));
        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(ai, profiles, users);
        verify(conversations, never()).save(any());
    }
    @Test void duplicateMatchRaceRemovesOnlyTheNewConversation() {
        MatchRepository matches = mock(MatchRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        when(profiles.findById("persona")).thenReturn(Optional.of(new Profile(
                "persona", "AI", "Persona", 25, "Indian", Gender.FEMALE, "Hello", "", "INTJ")));
        when(matches.save(any(Match.class))).thenThrow(new DuplicateKeyException("duplicate"));
        var service = new MatchService(matches, profiles, conversations, mock(UserService.class));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createMatch("alice", "persona"));
        assertEquals(409, error.getStatusCode().value());
        var saved = org.mockito.ArgumentCaptor.forClass(Conversation.class);
        verify(conversations).save(saved.capture());
        assertEquals("alice", saved.getValue().userId());
        verify(conversations).deleteById(saved.getValue().id());
    }
    @Test void missingMigrationOwnerDoesNotWriteAnything() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        var migration = new LegacyOwnershipMigration(mongo, users, "OWNER@example.com", "old-user");
        assertThrows(IllegalStateException.class, () -> migration.run(null));
        verifyNoInteractions(mongo);
    }
    @Test void concurrentConversationSaveReturnsConflict() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        UserService users = mock(UserService.class);
        Profile persona = new Profile("persona", "AI", "Persona", 25, "Indian", Gender.FEMALE, "Hello", "", "INTJ");
        when(conversations.findByIdAndUserId("chat", "alice"))
                .thenReturn(Optional.of(new Conversation("chat", "alice", "persona", List.of(), 3L)));
        when(profiles.findById("persona")).thenReturn(Optional.of(persona));
        when(users.getProfile("alice")).thenReturn(persona);
        var service = spy(new ConversationService(mock(ChatClient.class), profiles, conversations, users));
        doAnswer(i -> i.getArgument(0)).when(service).generateProfileResponse(any(), any(), any());
        when(conversations.save(any(Conversation.class)))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("conflict"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addMessage("alice", "chat", "Hello"));
        assertEquals(409, error.getStatusCode().value());
        verify(conversations).save(argThat(c -> c.version().equals(3L)
                && c.messages().getFirst().authorId().equals("alice")));
    }
    @Test void migrationAssignsOnlyUnownedDocuments() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(
                new UserAccount("owner-id", "owner@example.com", "", null, java.time.Instant.now())));
        new LegacyOwnershipMigration(mongo, users, "owner@example.com", "").run(null);
        var query = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        var update = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).updateMulti(query.capture(), update.capture(), eq(Match.class));
        assertTrue(query.getValue().getQueryObject().containsKey("userId"));
        assertNull(query.getValue().getQueryObject().get("userId"));
        assertEquals("owner-id", ((org.bson.Document) update.getValue().getUpdateObject().get("$set")).get("userId"));
        verify(mongo).updateMulti(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(Conversation.class));
        verify(mongo, never()).remove(any(org.springframework.data.mongodb.core.query.Query.class), eq(Profile.class));
    }
    @Test void startupSeedingPreservesExistingPersonas() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        when(profiles.existsById(anyString())).thenReturn(true);
        var service = new ProfileCreationService(mock(ChatClient.class), mock(ProfileTools.class),
                profiles, mock(org.springframework.web.client.RestClient.class));
        service.saveProfilesToDB();
        verify(profiles, never()).deleteAll();
        verify(profiles, never()).saveAll(any());
        verify(profiles, never()).insert(any(Profile.class));
    }
}
