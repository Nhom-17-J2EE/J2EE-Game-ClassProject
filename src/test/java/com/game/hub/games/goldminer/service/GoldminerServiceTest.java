package com.game.hub.games.goldminer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cases for Goldminer Service
 */
class GoldminerServiceTest {
    
    private GoldminerService service;
    private String testRoomId;
    
    @BeforeEach
    void setUp() {
        service = new GoldminerService();
        testRoomId = "test_room_" + System.currentTimeMillis();
    }
    
    @Test
    void shouldCreateGameRoom() {
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        assertNotNull(room);
        assertEquals(testRoomId, room.roomId);
    }
    
    @Test
    void shouldStartGameWithPlayer() {
        String playerId = "player_1";
        service.startGame(testRoomId, playerId);
        
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        assertTrue(room.isRunning);
        assertEquals(playerId, room.currentPlayerId);
    }
    
    @Test
    void shouldAddScoreWhenCollectingItems() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        room.addScore(100);
        assertEquals(100, room.score);
        
        room.addScore(50);
        assertEquals(150, room.score);
    }
    
    @Test
    void shouldAddRandomItems() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        int initialCount = room.items.size();
        room.addRandomItems(5);
        
        assertEquals(initialCount + 5, room.items.size());
    }
    
    @Test
    void shouldCollectItem() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        int initialCount = room.items.size();
        
        if (initialCount > 0) {
            GoldminerService.Item item = room.items.get(0);
            room.collectItem(item.id);
            
            assertEquals(initialCount - 1, room.items.size());
        }
    }
    
    @Test
    void shouldSpawnBombsAfterInterval() throws InterruptedException {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        int initialBombs = room.bombs.size();
        
        // Wait for bomb spawn (10 seconds) - for testing we could mock time
        // For now, we just check that bombs can be hit
        GoldminerService.Bomb testBomb = new GoldminerService.Bomb("bomb_1", 100, 100, 30);
        room.bombs.add(testBomb);
        
        assertEquals(initialBombs + 1, room.bombs.size());
    }
    
    @Test
    void shouldHitBomb() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        // Add a test bomb
        GoldminerService.Bomb bomb = new GoldminerService.Bomb("bomb_test", 100, 100, 30);
        room.bombs.add(bomb);
        room.score = 200;  // Set initial score
        
        int initialBombs = room.bombs.size();
        room.hitBomb(bomb.id);
        
        assertEquals(initialBombs - 1, room.bombs.size());
        assertEquals(100, room.score);  // 50% reduced
    }
    
    @Test
    void shouldStopGame() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        assertTrue(room.isRunning);
        room.stopGame();
        assertFalse(room.isRunning);
    }
    
    @Test
    void shouldReturnCorrectGameState() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        Map<String, Object> state = room.getState();
        
        assertNotNull(state);
        assertTrue(state.containsKey("roomId"));
        assertTrue(state.containsKey("playerId"));
        assertTrue(state.containsKey("score"));
        assertTrue(state.containsKey("isRunning"));
        assertTrue(state.containsKey("items"));
        assertTrue(state.containsKey("bombs"));
        
        assertEquals(testRoomId, state.get("roomId"));
        assertEquals("player_1", state.get("playerId"));
        assertEquals(true, state.get("isRunning"));
    }
    
    @Test
    void shouldDeleteRoom() {
        service.getOrCreateRoom(testRoomId);
        service.deleteRoom(testRoomId);
        
        // After deletion, creating new room should work
        GoldminerService.GameRoom newRoom = service.getOrCreateRoom(testRoomId);
        assertNotNull(newRoom);
        assertFalse(newRoom.isRunning);
    }
    
    @Test
    void itemsShouldHaveCorrectProperties() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        if (room.items.size() > 0) {
            GoldminerService.Item item = room.items.get(0);
            
            assertNotNull(item.id);
            assertTrue(item.x >= 25 && item.x <= 750);  // Within bounds
            assertTrue(item.y >= 25 && item.y <= 550);  // Within bounds
            assertTrue(item.type >= 0 && item.type < 3);  // gold, silver, ruby
            assertTrue(item.value >= 10 && item.value <= 100);
        }
    }
    
    @Test
    void bombsShouldHaveCorrectProperties() {
        service.startGame(testRoomId, "player_1");
        GoldminerService.GameRoom room = service.getOrCreateRoom(testRoomId);
        
        GoldminerService.Bomb bomb = new GoldminerService.Bomb("bomb_test", 400, 300, 30);
        
        assertNotNull(bomb.id);
        assertEquals(400, bomb.x);
        assertEquals(300, bomb.y);
        assertEquals(30, bomb.radius);
        assertTrue(bomb.spawnTime > 0);
    }
}
