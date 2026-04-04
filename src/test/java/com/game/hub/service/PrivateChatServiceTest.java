package com.game.hub.service;

import com.game.hub.entity.PrivateChatRecord;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.PrivateChatRecordRepository;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateChatServiceTest {

    @Test
    void buildChatBootstrapShouldRejectNonFriendUsers() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        FriendshipService friendshipService = mock(FriendshipService.class);
        PrivateChatRecordRepository chatRepo = mock(PrivateChatRecordRepository.class);
        WordFilterService wordFilterService = mock(WordFilterService.class);

        when(userRepo.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(userRepo.findById("u2")).thenReturn(Optional.of(user("u2", "Bob")));
        when(friendshipService.areFriends("u1", "u2")).thenReturn(false);

        PrivateChatService service = new PrivateChatService(userRepo, friendshipService, chatRepo, wordFilterService);
        PrivateChatService.ChatBootstrapResult result = service.buildChatBootstrap("u1", "u2");

        assertFalse(result.ok());
        assertTrue(result.error().contains("friends"));
    }

    @Test
    void saveMessageShouldPersistMessageForFriends() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        FriendshipService friendshipService = mock(FriendshipService.class);
        PrivateChatRecordRepository chatRepo = mock(PrivateChatRecordRepository.class);
        WordFilterService wordFilterService = mock(WordFilterService.class);

        when(userRepo.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(userRepo.findById("u2")).thenReturn(Optional.of(user("u2", "Bob")));
        when(friendshipService.areFriends("u1", "u2")).thenReturn(true);
        when(wordFilterService.filter("hello")).thenReturn("hello");
        when(chatRepo.save(any(PrivateChatRecord.class))).thenAnswer(invocation -> {
            PrivateChatRecord record = invocation.getArgument(0);
            record.setId(10L);
            record.setSentAt(LocalDateTime.of(2026, 2, 23, 10, 0, 0));
            return record;
        });

        PrivateChatService service = new PrivateChatService(userRepo, friendshipService, chatRepo, wordFilterService);
        PrivateChatService.SendResult result = service.saveMessage(" u1 ", "u2", "  hello  ", " cid-1 ");

        assertTrue(result.ok());
        assertEquals("u1__u2", result.roomKey());
        assertNotNull(result.payload());
        assertEquals(10L, result.payload().get("messageId"));
        assertEquals("cid-1", result.payload().get("clientMessageId"));
        assertEquals("PRIVATE_CHAT", result.payload().get("type"));
        assertEquals("hello", result.payload().get("message"));
        assertEquals("Alice", result.payload().get("senderName"));

        ArgumentCaptor<PrivateChatRecord> captor = ArgumentCaptor.forClass(PrivateChatRecord.class);
        verify(chatRepo).save(captor.capture());
        PrivateChatRecord saved = captor.getValue();
        assertEquals("u1__u2", saved.getRoomKey());
        assertEquals("u1", saved.getFromUserId());
        assertEquals("u2", saved.getToUserId());
        assertEquals("hello", saved.getContent());
        assertEquals("cid-1", saved.getClientMessageId());
    }

    @Test
    void saveMessageShouldReuseExistingRecordForSameClientMessageId() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        FriendshipService friendshipService = mock(FriendshipService.class);
        PrivateChatRecordRepository chatRepo = mock(PrivateChatRecordRepository.class);
        WordFilterService wordFilterService = mock(WordFilterService.class);

        when(userRepo.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(userRepo.findById("u2")).thenReturn(Optional.of(user("u2", "Bob")));
        when(friendshipService.areFriends("u1", "u2")).thenReturn(true);

        PrivateChatRecord existing = new PrivateChatRecord();
        existing.setId(15L);
        existing.setRoomKey("u1__u2");
        existing.setFromUserId("u1");
        existing.setToUserId("u2");
        existing.setSenderName("Alice");
        existing.setContent("hello");
        existing.setClientMessageId("cid-15");
        existing.setSentAt(LocalDateTime.of(2026, 2, 23, 10, 2));
        when(chatRepo.findFirstByRoomKeyAndClientMessageId("u1__u2", "cid-15")).thenReturn(Optional.of(existing));

        PrivateChatService service = new PrivateChatService(userRepo, friendshipService, chatRepo, wordFilterService);
        PrivateChatService.SendResult result = service.saveMessage("u1", "u2", "hello", "cid-15");

        assertTrue(result.ok());
        assertEquals(15L, result.payload().get("messageId"));
        assertEquals("cid-15", result.payload().get("clientMessageId"));
        verify(chatRepo, never()).save(any(PrivateChatRecord.class));
    }

    @Test
    void buildChatBootstrapShouldIncludeRecentHistoryInChronologicalOrder() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        FriendshipService friendshipService = mock(FriendshipService.class);
        PrivateChatRecordRepository chatRepo = mock(PrivateChatRecordRepository.class);
        WordFilterService wordFilterService = mock(WordFilterService.class);

        when(userRepo.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(userRepo.findById("u2")).thenReturn(Optional.of(user("u2", "Bob")));
        when(friendshipService.areFriends("u1", "u2")).thenReturn(true);

        PrivateChatRecord newer = new PrivateChatRecord();
        newer.setId(2L);
        newer.setRoomKey("u1__u2");
        newer.setFromUserId("u2");
        newer.setToUserId("u1");
        newer.setSenderName("Bob");
        newer.setContent("new");
        newer.setSentAt(LocalDateTime.of(2026, 2, 23, 10, 1));

        PrivateChatRecord older = new PrivateChatRecord();
        older.setId(1L);
        older.setRoomKey("u1__u2");
        older.setFromUserId("u1");
        older.setToUserId("u2");
        older.setSenderName("Alice");
        older.setContent("old");
        older.setSentAt(LocalDateTime.of(2026, 2, 23, 10, 0));

        when(chatRepo.findTop100ByRoomKeyOrderByIdDesc("u1__u2")).thenReturn(List.of(newer, older));

        PrivateChatService service = new PrivateChatService(userRepo, friendshipService, chatRepo, wordFilterService);
        PrivateChatService.ChatBootstrapResult result = service.buildChatBootstrap("u1", "u2");

        assertTrue(result.ok());
        assertEquals(2, result.messages().size());
        Map<String, Object> first = result.messages().get(0);
        Map<String, Object> second = result.messages().get(1);
        assertEquals(1L, first.get("messageId"));
        assertEquals(2L, second.get("messageId"));
        assertNull(first.get("clientMessageId"));
        assertEquals("old", first.get("message"));
        assertEquals("new", second.get("message"));
    }

    @Test
    void saveMessageShouldFilterBannedWords() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        FriendshipService friendshipService = mock(FriendshipService.class);
        PrivateChatRecordRepository chatRepo = mock(PrivateChatRecordRepository.class);
        WordFilterService wordFilterService = mock(WordFilterService.class);

        when(userRepo.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(userRepo.findById("u2")).thenReturn(Optional.of(user("u2", "Bob")));
        when(friendshipService.areFriends("u1", "u2")).thenReturn(true);
        // Mock word filter to replace "damn" with "***"
        when(wordFilterService.filter("this is damn bad")).thenReturn("this is *** bad");
        when(chatRepo.save(any(PrivateChatRecord.class))).thenAnswer(invocation -> {
            PrivateChatRecord record = invocation.getArgument(0);
            record.setId(20L);
            record.setSentAt(LocalDateTime.of(2026, 2, 23, 10, 5, 0));
            return record;
        });

        PrivateChatService service = new PrivateChatService(userRepo, friendshipService, chatRepo, wordFilterService);
        PrivateChatService.SendResult result = service.saveMessage("u1", "u2", "this is damn bad", null);

        assertTrue(result.ok());
        assertEquals("this is *** bad", result.payload().get("message"));

        ArgumentCaptor<PrivateChatRecord> captor = ArgumentCaptor.forClass(PrivateChatRecord.class);
        verify(chatRepo).save(captor.capture());
        PrivateChatRecord saved = captor.getValue();
        assertEquals("this is *** bad", saved.getContent());
    }

    private static UserAccount user(String id, String displayName) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName(displayName);
        return user;
    }
}
