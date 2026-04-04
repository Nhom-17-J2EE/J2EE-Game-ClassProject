# Goldminer Game

## Tổng quan

**Goldminer** là một trò chơi đơn giản nơi người chơi cần:
1. ⛏️ **Lấy vàng** - Nhấp vào các viên vàng để thu thập điểm
2. 💣 **Tránh bom** - Bom sẽ xuất hiện ngẫu nhiên mỗi 10 giây
3. 📊 **Tích lũy điểm** - Mỗi viên vàng có giá trị khác nhau

## Tính năng chính

### ⏰ Bom xuất hiện ngẫu nhiên (10 giây)
```
- Bom xuất hiện tự động mỗi 10 giây
- Vị trí ngẫu nhiên trên canvas
- Được biểu diễn bằng hình tròn đen với ngọn lửa
```

### 🏆 Hệ thống điểm
```
- Vàng (Gold): 10-100 điểm
- Bạc (Silver): 10-100 điểm
- Hồng ngọc (Ruby): 10-100 điểm
- Trúng bom: Mất 50% điểm hiện tại
```

### 📱 Gameplay
```
- Nhấp vào viên vàng để lấy
- Nhấp vào bom sẽ giảm 50% score
- Score hiển thị real-time
- Timer đếm ngược bom spawn
```

## Cấu trúc Backend

### GoldminerService (`service/GoldminerService.java`)
**Quản lý logic trò chơi:**

**Phương thức chính:**
```java
GameRoom getOrCreateRoom(String roomId)      // Tạo/lấy room
void startGame(String roomId, String playerId)
void stopGame(String roomId)
Map<String, Object> getRoomState(String roomId)
```

**GameRoom Class:**
- Quản lý items, bombs, score
- Tự động spawn bom mỗi 10 giây qua Thread daemon
- Thread-safe với synchronized methods

**Bomb Spawning Logic:**
```java
private void spawnBombsLoop() {
    while (isRunning) {
        long elapsedSinceLastSpawn = currentTime - lastBombSpawnTime;
        if (elapsedSinceLastSpawn >= 10000) {  // 10 giây
            spawnRandomBomb();
            lastBombSpawnTime = currentTime;
        }
        Thread.sleep(1000);
    }
}
```

### GoldminerWebSocketController (`websocket/GoldminerWebSocketController.java`)
**Xử lý realtime game actions:**

**WebSocket Endpoints:**
```
/app/goldminer.start       - Bắt đầu trò chơi
/app/goldminer.collect     - Lấy vàng
/app/goldminer.hit-bomb    - Trúng bom
/app/goldminer.stop        - Dừng trò chơi
```

**Broadcast Topics:**
```
/topic/goldminer.{roomId}  - Cập nhật trạng thái game
```

### GoldminerController (`controller/GoldminerController.java`)
**REST API Endpoints:**

```
GET    /games/goldminer                  - Trang chính
GET    /games/goldminer/room/{roomId}    - Trang room
GET    /games/goldminer/api/room/{id}    - Lấy trạng thái
POST   /games/goldminer/api/create-room  - Tạo room
POST   /games/goldminer/api/start        - Bắt đầu
POST   /games/goldminer/api/stop         - Dừng
```

## Frontend

### HTML Template (`templates/games/goldminer/index.html`)
**Giao diện:**
- Canvas 800x600 (game board)
- Hiển thị score, items count, bombs count
- Timer đếm ngược bom spawn
- Nút Start/Stop

**Canvas Interaction:**
- Click vào item → lấy vàng
- Click vào bom → confirm trước khi chịu penalty

### JavaScript Logic
**Kết nối WebSocket:**
```javascript
stompClient.subscribe('/topic/goldminer.' + roomId, handleGameMessage);
```

**Vẽ items:**
```javascript
// Gold (vàng) - màu vàng
// Silver (bạc) - màu bạc
// Ruby (hồng ngọc) - màu hồng
```

**Vẽ bombs:**
```javascript
// Hình tròn đen với ngọn lửa (fuse)
// Dynamic position
```

## Bomb Spawning Chi tiết

### Quy trình
1. **Game Start** → `lastBombSpawnTime = currentTime`
2. **Every 1 second** → Check elapsed time
3. **After 10 seconds** → `spawnRandomBomb()`
4. **Spawn Bom:**
   - Vị trí ngẫu nhiên trong bounds
   - ID duy nhất với timestamp
   - Broadcast tới tất cả clients
5. **Client render** → Vẽ bom trên canvas

### Vị trí spawn
```javascript
x: random(25, 750)   // Tránh rìa canvas
y: random(25, 550)
radius: 30px
```

### UI Timer
```javascript
updateBombTimer() {
  remaining = INTERVAL - elapsed;
  if (remaining <= 3000) {
    show warning color / animation
  }
}
```

## Test Coverage

**Test File:** `GoldminerServiceTest.java` (13 test cases)

```
✅ shouldCreateGameRoom
✅ shouldStartGameWithPlayer
✅ shouldAddScoreWhenCollectingItems
✅ shouldAddRandomItems
✅ shouldCollectItem
✅ shouldSpawnBombsAfterInterval
✅ shouldHitBomb
✅ shouldStopGame
✅ shouldReturnCorrectGameState
✅ shouldDeleteRoom
✅ itemsShouldHaveCorrectProperties
✅ bombsShouldHaveCorrectProperties
```

### Chạy test
```bash
./mvnw.cmd test -Dtest=GoldminerServiceTest
```

## Cài đặt & Cấu hình

### Các constant chính
```java
GAME_WIDTH = 800
GAME_HEIGHT = 600
BOMB_SPAWN_INTERVAL_MS = 10000  // 10 giây
```

## Cách sử dụng

### Tạo game room
```http
POST /games/goldminer/api/create-room
Response: { "roomId": "goldminer_..." }
```

### Bắt đầu chơi
```http
POST /games/goldminer/api/start
Body: { "roomId": "..." }
```

### Realtime gameplay
```javascript
// Send message
stompClient.send('/app/goldminer.collect', {}, 
  JSON.stringify({ roomId, itemId }));

// Receive update
stompClient.subscribe('/topic/goldminer.' + roomId, 
  (msg) => handleUpdate(JSON.parse(msg.body)));
```

## Performance Notes

### Threading
- **Game loop:** Daemon thread cho spawn bom
- **Thread-safe:** Sử dụng `synchronized` cho mutable state
- **Connection:** SockJS/STOMP qua WebSocket

### Memory
- Mỗi room lưu ~50 items + ~10 bombs trong memory
- Auto-cleanup khi room deleted
- No persistence (stateless per game session)

## Mở rộng tương lai

1. **Multiplayer Leaderboard** - Lưu high scores
2. **Power-ups** - Item đặc biệt tăng tốc độ
3. **Different Difficulties** - Spawn bom nhanh hơn
4. **Animation Effects** - Particle effects khi lấy vàng
5. **Sound Effects** - Audio khi lấy item / trúng bom
6. **Persistence** - Lưu game stats vào database

## Liên kết

- [GoldminerService](src/main/backend/java/com/game/hub/games/goldminer/service/GoldminerService.java)
- [GoldminerWebSocketController](src/main/backend/java/com/game/hub/games/goldminer/websocket/GoldminerWebSocketController.java)
- [GoldminerController](src/main/backend/java/com/game/hub/games/goldminer/controller/GoldminerController.java)
- [Template](src/main/frontend/templates/games/goldminer/index.html)
- [Tests](src/test/java/com/game/hub/games/goldminer/service/GoldminerServiceTest.java)

---

**Last Updated:** 2026-04-04  
**Version:** 1.0  
**Status:** Complete & Tested
