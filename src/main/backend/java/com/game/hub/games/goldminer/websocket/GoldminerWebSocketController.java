package com.game.hub.games.goldminer.websocket;

import com.game.hub.games.goldminer.service.GoldminerService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * WebSocket Controller cho Goldminer Game
 * Xử lý realtime game actions (lấy vàng, trúng bom, v.v.)
 */
@Controller
public class GoldminerWebSocketController {
    
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String GUEST_USER_ID = "GUEST_USER_ID";
    
    private final GoldminerService goldminerService;
    private final SimpMessagingTemplate messagingTemplate;
    
    public GoldminerWebSocketController(GoldminerService goldminerService,
                                        SimpMessagingTemplate messagingTemplate) {
        this.goldminerService = goldminerService;
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Bắt đầu trò chơi
     */
    @MessageMapping("/goldminer.start")
    public void startGame(StartGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null || message.roomId() == null || message.roomId().isBlank()) {
            return;
        }
        
        String userId = getSessionUserId(headers);
        if (userId == null) {
            return;
        }
        
        goldminerService.startGame(message.roomId(), userId);
        
        // Broadcast game state
        broadcastGameState(message.roomId());
    }
    
    /**
     * Lấy vàng (thu thập item)
     */
    @MessageMapping("/goldminer.collect")
    public void collectItem(CollectItemMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null || message.roomId() == null || message.itemId() == null) {
            return;
        }
        
        String userId = getSessionUserId(headers);
        if (userId == null) {
            return;
        }
        
        GoldminerService.GameRoom room = goldminerService.getOrCreateRoom(message.roomId());
        GoldminerService.Item item = room.items.stream()
            .filter(i -> i.id.equals(message.itemId()))
            .findFirst()
            .orElse(null);
        
        if (item != null) {
            room.collectItem(message.itemId());
            room.addScore(item.value);
            
            // Broadcast update
            messagingTemplate.convertAndSend("/topic/goldminer." + message.roomId(), Map.of(
                "type", "ITEM_COLLECTED",
                "itemId", message.itemId(),
                "value", item.value,
                "score", room.score,
                "itemsRemaining", room.items.size()
            ));
        }
    }
    
    /**
     * Trúng bom
     */
    @MessageMapping("/goldminer.hit-bomb")
    public void hitBomb(HitBombMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null || message.roomId() == null || message.bombId() == null) {
            return;
        }
        
        String userId = getSessionUserId(headers);
        if (userId == null) {
            return;
        }
        
        GoldminerService.GameRoom room = goldminerService.getOrCreateRoom(message.roomId());
        GoldminerService.Bomb bomb = room.bombs.stream()
            .filter(b -> b.id.equals(message.bombId()))
            .findFirst()
            .orElse(null);
        
        if (bomb != null) {
            room.hitBomb(message.bombId());
            
            // Penalty: mất 50% score
            long penalty = room.score / 2;
            room.addScore(-penalty);
            
            // Broadcast update
            messagingTemplate.convertAndSend("/topic/goldminer." + message.roomId(), Map.of(
                "type", "BOMB_HIT",
                "bombId", message.bombId(),
                "penalty", penalty,
                "score", room.score,
                "bombsRemaining", room.bombs.size()
            ));
        }
    }
    
    /**
     * Dừng trò chơi
     */
    @MessageMapping("/goldminer.stop")
    public void stopGame(StopGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null || message.roomId() == null) {
            return;
        }
        
        String userId = getSessionUserId(headers);
        if (userId == null) {
            return;
        }
        
        goldminerService.stopGame(message.roomId());
        
        GoldminerService.GameRoom room = goldminerService.getOrCreateRoom(message.roomId());
        messagingTemplate.convertAndSend("/topic/goldminer." + message.roomId(), Map.of(
            "type", "GAME_STOPPED",
            "finalScore", room.score
        ));
    }
    
    /**
     * Lấy trạng thái hiện tại
     */
    @MessageMapping("/goldminer.state")
    public void getGameState(GameStateMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null || message.roomId() == null) {
            return;
        }
        
        String userId = getSessionUserId(headers);
        if (userId == null) {
            return;
        }
        
        GoldminerService.GameRoom room = goldminerService.getOrCreateRoom(message.roomId());
        messagingTemplate.convertAndSendToUser(userId, "/topic/goldminer.state", room.getState());
    }
    
    /**
     * Broadcast trạng thái game cho tất cả
     */
    private void broadcastGameState(String roomId) {
        GoldminerService.GameRoom room = goldminerService.getOrCreateRoom(roomId);
        messagingTemplate.convertAndSend("/topic/goldminer." + roomId, Map.of(
            "type", "STATE_UPDATE",
            "state", room.getState()
        ));
    }
    
    /**
     * Lấy userId từ session
     */
    private String getSessionUserId(SimpMessageHeaderAccessor headers) {
        if (headers == null) {
            return null;
        }
        
        Object authUserId = headers.getSessionAttributes().get(AUTH_USER_ID);
        if (authUserId != null) {
            return authUserId.toString();
        }
        
        Object guestUserId = headers.getSessionAttributes().get(GUEST_USER_ID);
        if (guestUserId != null) {
            return guestUserId.toString();
        }
        
        return null;
    }
    
    // ============== DTOs ==============
    
    record StartGameMessage(String roomId) {}
    record CollectItemMessage(String roomId, String itemId) {}
    record HitBombMessage(String roomId, String bombId) {}
    record StopGameMessage(String roomId) {}
    record GameStateMessage(String roomId) {}
}
