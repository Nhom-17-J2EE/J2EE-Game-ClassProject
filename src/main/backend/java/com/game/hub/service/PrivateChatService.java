package com.game.hub.service;

import com.game.hub.entity.PrivateChatRecord;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.PrivateChatRecordRepository;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrivateChatService {
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final UserAccountRepository userAccountRepository;
    private final FriendshipService friendshipService;
    private final PrivateChatRecordRepository privateChatRecordRepository;
    private final WordFilterService wordFilterService;

    public PrivateChatService(UserAccountRepository userAccountRepository,
                              FriendshipService friendshipService,
                              PrivateChatRecordRepository privateChatRecordRepository,
                              WordFilterService wordFilterService) {
        this.userAccountRepository = userAccountRepository;
        this.friendshipService = friendshipService;
        this.privateChatRecordRepository = privateChatRecordRepository;
        this.wordFilterService = wordFilterService;
    }

    public ChatBootstrapResult buildChatBootstrap(String currentUserId, String friendId) {
        String normalizedCurrentUserId = trimToNull(currentUserId);
        String normalizedFriendId = trimToNull(friendId);

        if (normalizedCurrentUserId == null || normalizedFriendId == null) {
            return ChatBootstrapResult.error("Missing user id");
        }
        if (normalizedCurrentUserId.equals(normalizedFriendId)) {
            return ChatBootstrapResult.error("Cannot chat with yourself");
        }

        UserAccount currentUser = userAccountRepository.findById(normalizedCurrentUserId).orElse(null);
        UserAccount friend = userAccountRepository.findById(normalizedFriendId).orElse(null);
        if (currentUser == null || friend == null) {
            return ChatBootstrapResult.error("User not found");
        }
        if (!friendshipService.areFriends(normalizedCurrentUserId, normalizedFriendId)) {
            return ChatBootstrapResult.error("Only friends can use private chat");
        }

        String roomKey = roomKey(normalizedCurrentUserId, normalizedFriendId);
        return ChatBootstrapResult.success(
            currentUser.getId(),
            displayNameOf(currentUser),
            friend.getId(),
            displayNameOf(friend),
            roomKey,
            loadRecentMessages(roomKey)
        );
    }

    public SendResult saveMessage(String currentUserId, String friendId, String content) {
        return saveMessage(currentUserId, friendId, content, null);
    }

    public SendResult saveMessage(String currentUserId, String friendId, String content, String clientMessageId) {
        String normalizedCurrentUserId = trimToNull(currentUserId);
        String normalizedFriendId = trimToNull(friendId);
        String normalizedContent = trimToNull(content);
        String normalizedClientMessageId = normalizeClientMessageId(clientMessageId);

        if (normalizedCurrentUserId == null || normalizedFriendId == null) {
            return SendResult.error(null, normalizedCurrentUserId, "Missing user id");
        }
        if (normalizedCurrentUserId.equals(normalizedFriendId)) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "Cannot chat with yourself");
        }
        if (normalizedContent == null) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "Empty message");
        }
        if (normalizedContent.length() > MAX_MESSAGE_LENGTH) {
            normalizedContent = normalizedContent.substring(0, MAX_MESSAGE_LENGTH);
        }
        
        // Lọc từ ngữ bị cấm
        normalizedContent = wordFilterService.filter(normalizedContent);

        UserAccount currentUser = userAccountRepository.findById(normalizedCurrentUserId).orElse(null);
        UserAccount friend = userAccountRepository.findById(normalizedFriendId).orElse(null);
        if (currentUser == null || friend == null) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "User not found");
        }
        if (!friendshipService.areFriends(normalizedCurrentUserId, normalizedFriendId)) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "Only friends can chat");
        }

        String roomKey = roomKey(normalizedCurrentUserId, normalizedFriendId);
        if (normalizedClientMessageId != null) {
            PrivateChatRecord existing = privateChatRecordRepository
                .findFirstByRoomKeyAndClientMessageId(roomKey, normalizedClientMessageId)
                .orElse(null);
            if (existing != null) {
                return SendResult.success(roomKey, existing.getFromUserId(), payloadFor(existing));
            }
        }

        PrivateChatRecord record = new PrivateChatRecord();
        record.setRoomKey(roomKey);
        record.setFromUserId(currentUser.getId());
        record.setToUserId(friend.getId());
        record.setSenderName(displayNameOf(currentUser));
        record.setContent(normalizedContent);
        record.setClientMessageId(normalizedClientMessageId);

        PrivateChatRecord saved;
        try {
            saved = privateChatRecordRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedClientMessageId != null) {
                PrivateChatRecord existing = privateChatRecordRepository
                    .findFirstByRoomKeyAndClientMessageId(roomKey, normalizedClientMessageId)
                    .orElse(null);
                if (existing != null) {
                    return SendResult.success(roomKey, existing.getFromUserId(), payloadFor(existing));
                }
            }
            throw ex;
        }

        return SendResult.success(roomKey, saved.getFromUserId(), payloadFor(saved));
    }

    public String roomKey(String userIdA, String userIdB) {
        if (userIdA == null || userIdB == null) {
            return null;
        }
        return userIdA.compareTo(userIdB) <= 0
            ? (userIdA + "__" + userIdB)
            : (userIdB + "__" + userIdA);
    }

    private List<Map<String, Object>> loadRecentMessages(String roomKey) {
        List<PrivateChatRecord> records = privateChatRecordRepository.findTop100ByRoomKeyOrderByIdDesc(roomKey);
        List<Map<String, Object>> messages = new ArrayList<>(records.size());
        for (int i = records.size() - 1; i >= 0; i--) {
            messages.add(historyRowFor(records.get(i)));
        }
        return messages;
    }

    private Map<String, Object> payloadFor(PrivateChatRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", record.getId());
        payload.put("clientMessageId", record.getClientMessageId());
        payload.put("type", "PRIVATE_CHAT");
        payload.put("roomKey", record.getRoomKey());
        payload.put("fromUserId", record.getFromUserId());
        payload.put("toUserId", record.getToUserId());
        payload.put("senderName", record.getSenderName());
        payload.put("message", record.getContent());
        payload.put("sentAt", record.getSentAt() == null ? null : record.getSentAt().toString());
        return payload;
    }

    private Map<String, Object> historyRowFor(PrivateChatRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("messageId", record.getId());
        row.put("clientMessageId", record.getClientMessageId());
        row.put("fromUserId", record.getFromUserId());
        row.put("toUserId", record.getToUserId());
        row.put("senderName", record.getSenderName());
        row.put("message", record.getContent());
        row.put("sentAt", record.getSentAt() == null ? null : record.getSentAt().toString());
        return row;
    }

    private String displayNameOf(UserAccount user) {
        String displayName = trimToNull(user.getDisplayName());
        return displayName == null ? user.getId() : displayName;
    }

    private String normalizeClientMessageId(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 96 ? normalized.substring(0, 96) : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ChatBootstrapResult(boolean ok,
                                      String error,
                                      String currentUserId,
                                      String currentUserName,
                                      String friendId,
                                      String friendName,
                                      String roomKey,
                                      List<Map<String, Object>> messages) {
        public static ChatBootstrapResult error(String error) {
            return new ChatBootstrapResult(false, error, null, null, null, null, null, List.of());
        }

        public static ChatBootstrapResult success(String currentUserId,
                                                  String currentUserName,
                                                  String friendId,
                                                  String friendName,
                                                  String roomKey,
                                                  List<Map<String, Object>> messages) {
            return new ChatBootstrapResult(true, null, currentUserId, currentUserName, friendId, friendName, roomKey, messages);
        }

        public Map<String, Object> toMap() {
            if (!ok) {
                return Map.of("success", false, "error", error == null ? "Unknown error" : error);
            }
            return Map.of(
                "success", true,
                "currentUserId", currentUserId,
                "currentUserName", currentUserName,
                "friendId", friendId,
                "friendName", friendName,
                "roomKey", roomKey,
                "messages", messages
            );
        }
    }

    public record SendResult(boolean ok,
                             String error,
                             String roomKey,
                             String userId,
                             Map<String, Object> payload) {
        public static SendResult success(String roomKey, String userId, Map<String, Object> payload) {
            return new SendResult(true, null, roomKey, userId, payload);
        }

        public static SendResult error(String roomKey, String userId, String error) {
            return new SendResult(false, error, roomKey, userId, null);
        }
    }
}
