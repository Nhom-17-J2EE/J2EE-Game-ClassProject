# Chat Word Filter Feature

## Tổng quan

Tính năng **Word Filter (Lọc từ ngữ cấm)** được triển khai để tự động lọc và thay thế các từ ngữ bị cấm, tinh tế hoặc xúc phạm bằng `***` trong tất cả các tin nhắn chat của trò chơi.

## Các thành phần chính

### 1. WordFilterService
Xin mời tham khảo file `src/main/backend/java/com/game/hub/service/WordFilterService.java`

**Chứa các tính năng:**
- Lọc từ ngữ bị cấm (không phân biệt hoa/thường)
- Kiểm tra nội dung có chứa từ cấm
- Thêm/xóa từ cấm thủ công
- Lấy danh sách từ bị cấm hiện tại

**Các phương thức chính:**
```java
public String filter(String content)                    // Lọc từ cấm -> ***
public boolean containsBannedWords(String content)      // Kiểm tra có từ cấm
public void addBannedWord(String word)                  // Thêm từ cấm
public void removeBannedWord(String word)               // Xóa từ cấm
public Set<String> getBannedWords()                     // Lấy danh sách từ cấm
```

### 2. Tích hợp vào PrivateChatService
**File:** `src/main/backend/java/com/game/hub/service/PrivateChatService.java`

Phương thức `saveMessage()` sử dụng `WordFilterService` để lọc tin nhắn trước khi lưu vào database:

```java
// Lọc từ ngữ bị cấm
normalizedContent = wordFilterService.filter(normalizedContent);
```

### 3. Tích hợp vào Caro Game WebSocket
**File:** `src/main/backend/java/com/game/hub/games/caro/websocket/GameWebSocketController.java`

Phương thức `chat()` lọc tin nhắn trong game room trước khi broadcast:

```java
// Lọc từ ngữ bị cấm
text = wordFilterService.filter(text);
```

## Danh sách từ ngữ bị cấm

### Tiếng Việt
- Các từ miệt thị: chó, mèo, ngốc, ngu, khốn, éo, hèn, đồ chó, v.v.
- Các từ tục tĩu: địt, buồi, mẹ kiếp, v.v.
- Các cảnh báo: sex, porn, xxx

### English
- Lời chửi rủa: damn, hell, shit, fuck, bitch, bastard
- Từ xúc phạm: asshole, idiot, retard, stupid
- Nội dung không phù hợp: sex, porn, xxx, rape, murder, kill, death

## Cách sử dụng

### Lọc tin nhắn tự động
Khi người dùng gửi tin nhắn qua:
1. **Private Chat**: Tin nhắn tự động lọc qua `PrivateChatService.saveMessage()`
2. **Game Room Chat (Caro)**: Tin nhắn tự động lọc qua `GameWebSocketController.chat()`

### Thêm từ cấm mới (Runtime)
```java
@Autowired
private WordFilterService wordFilterService;

public void addCustomBannedWord(String word) {
    wordFilterService.addBannedWord(word);
}
```

### Xóa từ khỏi danh sách cấm (Runtime)
```java
public void removeBannedWord(String word) {
    wordFilterService.removeBannedWord(word);
}
```

### Kiểm tra nếu tin nhắn chứa từ cấm
```java
if (wordFilterService.containsBannedWords(userInput)) {
    // Log hoặc thực hiện hành động
}
```

## Chi tiết kỹ thuật

### Regex Pattern
Bộ lọc sử dụng regex pattern với **word boundary** để tránh lọc substring:
```regex
\b<word>\b  (Case-insensitive)
```

Ví dụ:
- "damn" ✓ được lọc → "***"
- "DAMN" ✓ được lọc → "***"
- "damnation" ✗ KHÔNG được lọc (từ khác)

### Database
- Tin nhắn được lưu **sau khi lọc**
- Dữ liệu lịch sử chat chứa nội dung đã được lọc

### WebSocket
- Private Chat: Sử dụng STOMP/SockJS, message lọc qua service
- Game Room Chat: Sử dụng STOMP/SockJS, message lọc trong controller

## Test Coverage

### Test Files
1. **WordFilterServiceTest** - Test chi tiết các chức năng lọc
   - Test lọc từ đơn và từ ghép
   - Test case-insensitive
   - Test word boundaries
   - Test custom words

2. **PrivateChatServiceTest** - Test tích hợp
   - Test `saveMessageShouldFilterBannedWords()`

### Chạy Test
```bash
./mvnw.cmd test -Dtest=WordFilterServiceTest
./mvnw.cmd test -Dtest=PrivateChatServiceTest
```

## Quản lý danh sách từ cấm

### Cập nhật danh sách từ cấm (Hiện tại - Static)
Để thêm/xóa từ cấm, chỉnh sửa static initializer trong `WordFilterService`:

```java
static {
    BANNED_WORDS.addAll(Set.of(
        // Thêm từ mới ở đây
        "newword1",
        "newword2"
    ));
}
```

### Tương lai - Dynamic Management (Optional)
Có thể mở rộng để:
1. Lưu danh sách từ cấm trong database
2. Admin API để quản lý động
3. Cache danh sách cho performance

## Performance

### Optimization
- **Danh sách từ từ:** Sử dụng `HashSet` cho O(1) lookup
- **Regex compilation:** Compiled khi filter, có thể cache nếu cần
- **Message length:** Giới hạn 2000 ký tự tránh tin nhắn quá dài

### Impact
- Thêm ~5-10ms cho mỗi tin nhắn (tùy kích thước message)
- Không ảnh hưởng đáng kể đến latency

## Logging & Monitoring

Hiện tại: Không có logging explicit. Để thêm:

```java
if (wordFilterService.containsBannedWords(originalContent)) {
    logger.warn("Banned message detected from user: {}", userId);
}
```

## Lưu ý bảo mật

1. **Lọc server-side:** Tất cả lọc xảy ra trên server, tránh bypass từ client
2. **Case-insensitive:** Người dùng không thể bypass bằng uppercase/lowercase
3. **Word boundary:** Tránh lọc từ hợp pháp chứa từ cấm

## Liên kết liên quan

- [PrivateChatService](src/main/backend/java/com/game/hub/service/PrivateChatService.java)
- [GameWebSocketController (Caro)](src/main/backend/java/com/game/hub/games/caro/websocket/GameWebSocketController.java)
- [WordFilterServiceTest](src/test/java/com/game/hub/service/WordFilterServiceTest.java)
- [PrivateChatServiceTest](src/test/java/com/game/hub/service/PrivateChatServiceTest.java)

---

**Last Updated:** 2026-04-04
**Version:** 1.0
