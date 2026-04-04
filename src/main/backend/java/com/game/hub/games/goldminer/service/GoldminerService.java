package com.game.hub.games.goldminer.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service cho Goldminer Game
 * Quản lý trạng thái trò chơi, items, bom, và score
 */
@Service
public class GoldminerService {
    
    private static final int GAME_WIDTH = 800;
    private static final int GAME_HEIGHT = 600;
    private static final int BOMB_SPAWN_INTERVAL_MS = 10000;  // 10 giây
    private static final Random RANDOM = new Random();
    
    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    
    /**
     * Tạo / lấy game room
     */
    public GameRoom getOrCreateRoom(String roomId) {
        return gameRooms.computeIfAbsent(roomId, k -> new GameRoom(roomId));
    }
    
    /**
     * Xóa game room
     */
    public void deleteRoom(String roomId) {
        GameRoom room = gameRooms.remove(roomId);
        if (room != null) {
            room.stop();
        }
    }
    
    /**
     * Bắt đầu trò chơi
     */
    public void startGame(String roomId, String playerId) {
        GameRoom room = getOrCreateRoom(roomId);
        room.startGame(playerId);
    }
    
    /**
     * Dừng trò chơi
     */
    public void stopGame(String roomId) {
        GameRoom room = gameRooms.get(roomId);
        if (room != null) {
            room.stopGame();
        }
    }
    
    /**
     * Thêm item (vàng) vào trò chơi
     */
    public void addRandomItems(String roomId, int count) {
        GameRoom room = gameRooms.get(roomId);
        if (room != null) {
            room.addRandomItems(count);
        }
    }
    
    /**
     * Lấy trạng thái room
     */
    public Map<String, Object> getRoomState(String roomId) {
        GameRoom room = gameRooms.get(roomId);
        if (room == null) {
            return Map.of("error", "Room not found");
        }
        return room.getState();
    }
    
    /**
     * Model cho GameRoom
     */
    public static class GameRoom {
        private final String roomId;
        private final List<Item> items = new ArrayList<>();
        private final List<Bomb> bombs = new ArrayList<>();
        private String currentPlayerId;
        private long score = 0;
        private long startTime = 0;
        private long lastBombSpawnTime = 0;
        private boolean isRunning = false;
        private final Thread bombSpawnerThread;
        
        public GameRoom(String roomId) {
            this.roomId = roomId;
            // Thread để spawn bom mỗi 10 giây
            this.bombSpawnerThread = new Thread(() -> spawnBombsLoop());
            this.bombSpawnerThread.setDaemon(true);
        }
        
        public synchronized void startGame(String playerId) {
            this.currentPlayerId = playerId;
            this.score = 0;
            this.startTime = System.currentTimeMillis();
            this.lastBombSpawnTime = this.startTime;
            this.isRunning = true;
            this.items.clear();
            this.bombs.clear();
            
            // Thêm items ban đầu
            addRandomItems(10);
            
            // Bắt đầu thread spawn bom
            if (!bombSpawnerThread.isAlive()) {
                new Thread(() -> spawnBombsLoop()).start();
            }
        }
        
        public synchronized void stopGame() {
            this.isRunning = false;
        }
        
        public synchronized void stop() {
            this.isRunning = false;
            // Có thể interrupt thread nếu cần
        }
        
        /**
         * Thread loop để spawn bom mỗi 10 giây
         */
        private void spawnBombsLoop() {
            while (isRunning) {
                try {
                    long currentTime = System.currentTimeMillis();
                    long elapsedSinceLastSpawn = currentTime - lastBombSpawnTime;
                    
                    if (elapsedSinceLastSpawn >= BOMB_SPAWN_INTERVAL_MS && isRunning) {
                        spawnRandomBomb();
                        lastBombSpawnTime = currentTime;
                    }
                    
                    // Check mỗi 1 giây
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        /**
         * Spawn bom ngẫu nhiên
         */
        private synchronized void spawnRandomBomb() {
            if (!isRunning) {
                return;
            }
            
            int x = RANDOM.nextInt(GAME_WIDTH - 50) + 25;
            int y = RANDOM.nextInt(GAME_HEIGHT - 50) + 25;
            
            Bomb bomb = new Bomb(
                "bomb_" + System.currentTimeMillis() + "_" + RANDOM.nextInt(10000),
                x,
                y,
                30  // bomb radius
            );
            
            bombs.add(bomb);
        }
        
        /**
         * Thêm items ngẫu nhiên
         */
        public synchronized void addRandomItems(int count) {
            for (int i = 0; i < count; i++) {
                int x = RANDOM.nextInt(GAME_WIDTH - 50) + 25;
                int y = RANDOM.nextInt(GAME_HEIGHT - 50) + 25;
                int value = 10 + RANDOM.nextInt(90);  // 10-100
                int type = RANDOM.nextInt(3);  // 0: gold, 1: silver, 2: ruby
                
                Item item = new Item(
                    "item_" + System.currentTimeMillis() + "_" + i,
                    x,
                    y,
                    type,
                    value
                );
                items.add(item);
            }
        }
        
        /**
         * Xóa item sau khi lấy
         */
        public synchronized void collectItem(String itemId) {
            items.removeIf(item -> item.id.equals(itemId));
        }
        
        /**
         * Hit bom
         */
        public synchronized void hitBomb(String bombId) {
            bombs.removeIf(bomb -> bomb.id.equals(bombId));
        }
        
        /**
         * Cập nhật score
         */
        public synchronized void addScore(long points) {
            this.score += points;
        }
        
        /**
         * Lấy trạng thái hiện tại
         */
        public synchronized Map<String, Object> getState() {
            Map<String, Object> state = new HashMap<>();
            state.put("roomId", roomId);
            state.put("playerId", currentPlayerId);
            state.put("score", score);
            state.put("isRunning", isRunning);
            state.put("elapsedTime", System.currentTimeMillis() - startTime);
            
            // Items
            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (Item item : items) {
                itemsList.add(item.toMap());
            }
            state.put("items", itemsList);
            
            // Bombs
            List<Map<String, Object>> bombsList = new ArrayList<>();
            for (Bomb bomb : bombs) {
                bombsList.add(bomb.toMap());
            }
            state.put("bombs", bombsList);
            state.put("timeUntilNextBomb", BOMB_SPAWN_INTERVAL_MS - (System.currentTimeMillis() - lastBombSpawnTime));
            
            return state;
        }
    }
    
    /**
     * Model cho Item (vàng, bạc, v.v.)
     */
    public static class Item {
        public String id;
        public int x;
        public int y;
        public int type;  // 0: gold, 1: silver, 2: ruby
        public int value;
        public long spawnTime;
        
        public Item(String id, int x, int y, int type, int value) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.type = type;
            this.value = value;
            this.spawnTime = System.currentTimeMillis();
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "x", x,
                "y", y,
                "type", type,
                "value", value,
                "spawnTime", spawnTime
            );
        }
    }
    
    /**
     * Model cho Bomb
     */
    public static class Bomb {
        public String id;
        public int x;
        public int y;
        public int radius;
        public long spawnTime;
        
        public Bomb(String id, int x, int y, int radius) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.spawnTime = System.currentTimeMillis();
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "x", x,
                "y", y,
                "radius", radius,
                "spawnTime", spawnTime
            );
        }
    }
}
