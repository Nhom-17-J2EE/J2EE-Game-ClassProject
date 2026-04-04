package com.game.hub.games.caro.websocket;

import com.game.hub.service.AchievementService;
import com.game.hub.service.WordFilterService;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.games.caro.service.GameRoomService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Controller
public class GameWebSocketController {
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String GUEST_USER_ID = "GUEST_USER_ID";
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";
    private static final long ROUND_RESET_DELAY_MS = 1500L;

    private final GameRoomService gameRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserAccountRepository userAccountRepository;
    private final AchievementService achievementService;
    private final WordFilterService wordFilterService;
    private final Map<String, RoomPresence> sessionRoomPresence = new ConcurrentHashMap<>();

    public GameWebSocketController(GameRoomService gameRoomService,
                                   SimpMessagingTemplate messagingTemplate,
                                   UserAccountRepository userAccountRepository,
                                   AchievementService achievementService,
                                   WordFilterService wordFilterService) {
        this.gameRoomService = gameRoomService;
        this.messagingTemplate = messagingTemplate;
        this.userAccountRepository = userAccountRepository;
        this.achievementService = achievementService;
        this.wordFilterService = wordFilterService;
    }

    @MessageMapping("/game.join")
    public void join(JoinGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireConnectionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        GameRoomService.JoinResult result = gameRoomService.joinRoom(message.getRoomId(), userId);
        if (!result.ok()) {
            sendUserError(message.getRoomId(), userId, result.error());
            return;
        }
        rememberRoomPresence(headers, message.getRoomId(), userId);

        PlayerMeta playerMeta = playerMeta(userId);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "PLAYER_JOINED",
            "userId", userId,
            "symbol", result.symbol(),
            "currentTurnUserId", result.currentTurnUserId() == null ? "" : result.currentTurnUserId(),
            "playerCount", result.playerCount(),
            "spectatorCount", result.spectatorCount(),
            "board", gameRoomService.getBoardSnapshot(message.getRoomId()),
            "displayName", playerMeta.displayName(),
            "avatarPath", playerMeta.avatarPath()
        ));

        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }
    
    @MessageMapping("/game.spectate")
    public void spectate(JoinGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireConnectionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        GameRoomService.JoinResult result = gameRoomService.joinAsSpectator(message.getRoomId(), userId);
        if (!result.ok()) {
            sendUserError(message.getRoomId(), userId, result.error());
            return;
        }
        rememberRoomPresence(headers, message.getRoomId(), userId);

        PlayerMeta playerMeta = playerMeta(userId);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "SPECTATOR_JOINED",
            "userId", userId,
            "spectatorCount", result.spectatorCount(),
            "displayName", playerMeta.displayName(),
            "avatarPath", playerMeta.avatarPath()
        ));
    }

    @MessageMapping("/game.move")
    public void move(MoveMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireConnectionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        GameRoomService.MoveResult result = gameRoomService.makeMove(message.getRoomId(), userId, message.getX(), message.getY());
        if (!result.ok()) {
            sendUserError(message.getRoomId(), userId, result.error());
            return;
        }

        Map<String, Object> movePayload = new HashMap<>();
        movePayload.put("type", "MOVE");
        movePayload.put("x", result.x());
        movePayload.put("y", result.y());
        movePayload.put("symbol", result.symbol());
        if (result.nextTurnUserId() != null) {
            movePayload.put("nextTurnUserId", result.nextTurnUserId());
        }
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), movePayload);

        if (result.win()) {
            achievementService.checkAndAward(result.winnerUserId(), "Caro", true);
            achievementService.checkAndAward(result.loserUserId(), "Caro", false);
            Map<String, Object> gameOverPayload = new HashMap<>();
            gameOverPayload.put("type", "GAME_OVER");
            gameOverPayload.put("winnerUserId", result.winnerUserId());
            gameOverPayload.put("resetDelayMs", ROUND_RESET_DELAY_MS);
            if (result.loserUserId() != null) {
                gameOverPayload.put("loserUserId", result.loserUserId());
            }
            if (result.winnerScore() != null) {
                gameOverPayload.put("winnerScore", result.winnerScore());
            }
            if (result.loserScore() != null) {
                gameOverPayload.put("loserScore", result.loserScore());
            }
            if (result.winLine() != null) {
                gameOverPayload.put("winLine", result.winLine());
            }
            messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), gameOverPayload);
            scheduleRoundReset(message.getRoomId());
            return;
        }

        if (result.draw()) {
            Map<String, Object> drawPayload = new HashMap<>();
            drawPayload.put("type", "DRAW");
            drawPayload.put("resetDelayMs", ROUND_RESET_DELAY_MS);
            messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), drawPayload);
            scheduleRoundReset(message.getRoomId());
        }
    }

    @MessageMapping("/game.leave")
    public void leave(LeaveGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireConnectionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        gameRoomService.leaveRoom(message.getRoomId(), userId);
        forgetRoomPresence(headers, message.getRoomId(), userId);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "PLAYER_LEFT",
            "userId", userId
        ));
        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }

    @MessageMapping("/game.surrender")
    public void surrender(LeaveGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireConnectionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }

        GameRoomService.SurrenderResult result = gameRoomService.surrender(message.getRoomId(), userId);
        if (!result.ok()) {
            sendUserError(message.getRoomId(), userId, result.error());
            return;
        }

        achievementService.checkAndAward(result.winnerUserId(), "Caro", true);
        achievementService.checkAndAward(result.loserUserId(), "Caro", false);

        Map<String, Object> gameOverPayload = new HashMap<>();
        gameOverPayload.put("type", "GAME_OVER");
        gameOverPayload.put("winnerUserId", result.winnerUserId());
        gameOverPayload.put("loserUserId", result.loserUserId());
        gameOverPayload.put("surrenderUserId", userId);
        gameOverPayload.put("reason", "SURRENDER");
        gameOverPayload.put("resetDelayMs", ROUND_RESET_DELAY_MS);
        if (result.winnerScore() != null) {
            gameOverPayload.put("winnerScore", result.winnerScore());
        }
        if (result.loserScore() != null) {
            gameOverPayload.put("loserScore", result.loserScore());
        }
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), gameOverPayload);
        scheduleRoundReset(message.getRoomId());
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RoomPresence presence = sessionRoomPresence.remove(sessionId);
        if (presence == null) {
            return;
        }

        gameRoomService.leaveRoom(presence.roomId(), presence.userId());
        messagingTemplate.convertAndSend("/topic/room." + presence.roomId(), Map.of(
            "type", "PLAYER_LEFT",
            "userId", presence.userId()
        ));
        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }

    @MessageMapping("/game.chat")
    public void chat(ChatMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null || message.getRoomId() == null || message.getRoomId().isBlank()) {
            return;
        }
        String userId = requireConnectionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        String text = message.getContent() == null ? "" : message.getContent().trim();
        if (text.isBlank()) {
            return;
        }
        
        // Lọc từ ngữ bị cấm
        text = wordFilterService.filter(text);
        
        PlayerMeta playerMeta = playerMeta(userId);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "CHAT",
            "userId", userId,
            "displayName", playerMeta.displayName(),
            "avatarPath", playerMeta.avatarPath(),
            "message", text
        ));
    }

    private void sendUserError(String roomId, String userId, String error) {
        if (userId != null && !userId.isBlank()) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/errors", Map.of("error", error));
        }
        if (roomId != null && !roomId.isBlank()) {
            messagingTemplate.convertAndSend("/topic/room." + roomId, Map.of(
                "type", "ERROR",
                "userId", userId,
                "error", error
            ));
        }
    }

    private void scheduleRoundReset(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            gameRoomService.resetRoom(roomId);
            messagingTemplate.convertAndSend("/topic/room." + roomId, Map.of(
                "type", "RESET",
                "board", gameRoomService.getBoardSnapshot(roomId),
                "currentTurnUserId", gameRoomService.getCurrentTurnUserId(roomId)
            ));
        }, CompletableFuture.delayedExecutor(ROUND_RESET_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private void rememberRoomPresence(SimpMessageHeaderAccessor headers, String roomId, String userId) {
        String sessionId = headers == null ? null : headers.getSessionId();
        if (sessionId == null || sessionId.isBlank() || roomId == null || roomId.isBlank() || userId == null || userId.isBlank()) {
            return;
        }
        RoomPresence previous = sessionRoomPresence.put(sessionId, new RoomPresence(roomId, userId));
        if (previous == null || (previous.roomId().equals(roomId) && previous.userId().equals(userId))) {
            return;
        }
        gameRoomService.leaveRoom(previous.roomId(), previous.userId());
        messagingTemplate.convertAndSend("/topic/room." + previous.roomId(), Map.of(
            "type", "PLAYER_LEFT",
            "userId", previous.userId()
        ));
        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }

    private void forgetRoomPresence(SimpMessageHeaderAccessor headers, String roomId, String userId) {
        String sessionId = headers == null ? null : headers.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RoomPresence current = sessionRoomPresence.get(sessionId);
        if (current == null) {
            return;
        }
        if (!current.roomId().equals(roomId) || !current.userId().equals(userId)) {
            return;
        }
        sessionRoomPresence.remove(sessionId, current);
    }

    private String requireConnectionUser(String roomId,
                                         String claimedUserId,
                                         SimpMessageHeaderAccessor headers) {
        String connectionUserId = connectionUserId(headers);
        if (connectionUserId == null) {
            sendUserError(roomId, claimedUserId, "Session user not found");
            return null;
        }
        if (claimedUserId == null || claimedUserId.isBlank()) {
            sendUserError(roomId, connectionUserId, "UserId is required");
            return null;
        }
        if (!connectionUserId.equals(claimedUserId)) {
            sendUserError(roomId, connectionUserId, "Session user mismatch");
            return null;
        }
        return connectionUserId;
    }

    private String connectionUserId(SimpMessageHeaderAccessor headers) {
        if (headers != null && headers.getSessionAttributes() != null) {
            String authUserId = asNonBlank(headers.getSessionAttributes().get(AUTH_USER_ID));
            if (authUserId != null) {
                return authUserId;
            }
            String guestUserId = asNonBlank(headers.getSessionAttributes().get(GUEST_USER_ID));
            if (guestUserId != null) {
                return guestUserId;
            }
        }
        if (headers != null && headers.getUser() != null) {
            return asNonBlank(headers.getUser().getName());
        }
        return null;
    }

    private PlayerMeta playerMeta(String userId) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            if (isGuestUserId(userId)) {
                return new PlayerMeta(guestDisplayName(userId), DEFAULT_AVATAR_PATH);
            }
            return new PlayerMeta(userId, DEFAULT_AVATAR_PATH);
        }
        String displayName = user.getDisplayName() == null || user.getDisplayName().isBlank()
            ? userId
            : user.getDisplayName();
        String avatarPath = user.getAvatarPath() == null || user.getAvatarPath().isBlank()
            ? DEFAULT_AVATAR_PATH
            : user.getAvatarPath();
        return new PlayerMeta(displayName, avatarPath);
    }

    private boolean isGuestUserId(String userId) {
        return userId != null && userId.trim().toLowerCase().startsWith("guest-");
    }

    private String guestDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "Guest";
        }
        String normalized = userId.trim();
        String suffix = normalized.length() <= 4
            ? normalized
            : normalized.substring(normalized.length() - 4);
        return "Guest " + suffix.toUpperCase();
    }

    private String asNonBlank(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private record PlayerMeta(String displayName, String avatarPath) {
    }

    private record RoomPresence(String roomId, String userId) {
    }
}
