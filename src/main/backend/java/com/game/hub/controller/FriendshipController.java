package com.game.hub.controller;

import com.game.hub.entity.AchievementNotification;
import com.game.hub.entity.Friendship;
import com.game.hub.entity.SystemNotification;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.AchievementNotificationRepository;
import com.game.hub.repository.SystemNotificationRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.FriendshipService;
import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Friendship feature
 * Handles: friend requests, acceptance, decline, removal, search, and notifications
 */
@Controller
@RequestMapping("/friendship")
public class FriendshipController {
    private final FriendshipService friendshipService;
    private final UserAccountRepository userAccountRepository;
    private final AchievementNotificationRepository achievementNotificationRepository;
    private final SystemNotificationRepository systemNotificationRepository;
    private final ProfileStatsService profileStatsService;

    public FriendshipController(FriendshipService friendshipService,
                                UserAccountRepository userAccountRepository,
                                AchievementNotificationRepository achievementNotificationRepository,
                                SystemNotificationRepository systemNotificationRepository,
                                ProfileStatsService profileStatsService) {
        this.friendshipService = friendshipService;
        this.userAccountRepository = userAccountRepository;
        this.achievementNotificationRepository = achievementNotificationRepository;
        this.systemNotificationRepository = systemNotificationRepository;
        this.profileStatsService = profileStatsService;
    }

    // ============== PAGE ENDPOINTS ==============

    @GetMapping
    public String page(HttpServletRequest request, Model model) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return "redirect:/account/login-page";
        }
        model.addAttribute("currentUserId", userId);
        return "friendship/index";
    }

    @GetMapping("/notifications")
    public String notificationsPage(HttpServletRequest request, Model model) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return "redirect:/account/login-page";
        }
        model.addAttribute("currentUserId", userId);
        return "friendship/notifications";
    }

    @GetMapping("/search")
    public String searchPage(@RequestParam String query, HttpServletRequest request, Model model) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return "redirect:/account/login-page";
        }
        model.addAttribute("currentUserId", userId);
        model.addAttribute("query", query);
        return "friendship/search";
    }

    @GetMapping("/user-detail/{id}")
    public String userDetailPage(@PathVariable String id, HttpServletRequest request, Model model) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return "redirect:/account/login-page";
        }
        if (!userAccountRepository.existsById(id)) {
            return "redirect:/friendship";
        }
        model.addAttribute("currentUserId", userId);
        model.addAttribute("userId", id);
        return "friendship/user-detail";
    }

    // ============== API: FRIENDSHIP ACTIONS ==============

    /**
     * Gửi lời mời kết bạn bằng email
     */
    @PostMapping("/api/send-request")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendRequest(@RequestBody SendRequestDto dto, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (dto == null || dto.email() == null || dto.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email is required"));
        }

        UserAccount target = userAccountRepository.findByEmail(dto.email()).orElse(null);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "User not found"));
        }

        boolean success = friendshipService.sendRequest(userId, target.getId());
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot send friend request"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Friend request sent"));
    }

    /**
     * Gửi lời mời kết bạn bằng user ID
     */
    @PostMapping("/api/send-request-by-id")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendRequestById(@RequestBody SendRequestByIdDto dto, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (dto == null || dto.addresseeId() == null || dto.addresseeId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "User ID is required"));
        }

        if (!userAccountRepository.existsById(dto.addresseeId())) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "User not found"));
        }

        boolean success = friendshipService.sendRequest(userId, dto.addresseeId());
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot send friend request"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Friend request sent"));
    }

    /**
     * Chấp nhận lời mời kết bạn
     */
    @PostMapping("/api/accept")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> acceptRequest(@RequestBody ActionDto dto, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (dto == null || dto.friendshipId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Friendship ID is required"));
        }

        boolean success = friendshipService.acceptRequest(dto.friendshipId(), userId);
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot accept friend request"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Friend request accepted"));
    }

    /**
     * Từ chối lời mời kết bạn
     */
    @PostMapping("/api/decline")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> declineRequest(@RequestBody ActionDto dto, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (dto == null || dto.friendshipId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Friendship ID is required"));
        }

        boolean success = friendshipService.declineRequest(dto.friendshipId(), userId);
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot decline friend request"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Friend request declined"));
    }

    /**
     * Xóa bạn
     */
    @PostMapping("/api/remove")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeFriend(@RequestBody RemoveFriendDto dto, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (dto == null || dto.friendId() == null || dto.friendId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Friend ID is required"));
        }

        boolean success = friendshipService.removeFriendship(userId, dto.friendId());
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot remove friendship"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Friend removed"));
    }

    // ============== API: DATA RETRIEVAL ==============

    /**
     * Lấy danh sách bạn bè của người dùng hiện tại
     */
    @GetMapping("/api/friends")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFriends(HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        List<UserAccount> friends = friendshipService.getFriends(userId);
        List<FriendView> views = friends.stream().map(this::toFriendView).toList();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "friends", views,
            "count", views.size()
        ));
    }

    /**
     * Lấy danh sách lời mời chưa được chấp nhận
     */
    @GetMapping("/api/pending-requests")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPendingRequests(HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        List<Friendship> requests = friendshipService.getPendingRequests(userId);
        List<FriendRequestView> views = buildFriendRequestViews(requests);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "requests", views,
            "count", views.size()
        ));
    }

    /**
     * Lấy danh sách lời mời gửi đi chưa được chấp nhận
     */
    @GetMapping("/api/sent-requests")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSentRequests(HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        List<Friendship> requests = friendshipService.getSentRequests(userId);
        List<SentRequestView> views = buildSentRequestViews(requests);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "requests", views,
            "count", views.size()
        ));
    }

    /**
     * Tìm kiếm người dùng
     */
    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", "",
                "exactMatches", List.of(),
                "similarMatches", List.of()
            ));
        }

        String normalized = query.toLowerCase();
        List<UserAccount> allUsers = userAccountRepository.findAll();

        // Exact matches
        List<UserAccount> exactMatches = allUsers.stream()
            .filter(u -> (u.getDisplayName() != null && u.getDisplayName().toLowerCase().equals(normalized))
                || (u.getEmail() != null && u.getEmail().toLowerCase().equals(normalized)))
            .toList();

        // Similar matches
        List<UserAccount> similarMatches;
        if (!exactMatches.isEmpty()) {
            var exactIds = exactMatches.stream().map(UserAccount::getId).toList();
            similarMatches = allUsers.stream()
                .filter(u -> ((u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(normalized))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(normalized)))
                    && !exactIds.contains(u.getId()))
                .toList();
        } else {
            similarMatches = allUsers.stream()
                .filter(u -> {
                    String name = u.getDisplayName() == null ? "" : u.getDisplayName().toLowerCase();
                    String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase();
                    int match = Math.max(longestCommonSubstringLength(name, normalized),
                        longestCommonSubstringLength(email, normalized));
                    return match >= 5;
                })
                .toList();
        }

        List<UserSearchView> exactViews = exactMatches.stream().map(this::toUserSearchView).toList();
        List<UserSearchView> similarViews = similarMatches.stream().map(this::toUserSearchView).toList();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "query", query,
            "exactMatches", exactViews,
            "similarMatches", similarViews
        ));
    }

    /**
     * Lấy thông tin và trạng thái kết bạn với một người dùng cụ thể
     */
    @GetMapping("/api/user-detail/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable String id, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (id == null || id.isBlank() || !userAccountRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "User not found"));
        }

        UserAccount targetUser = userAccountRepository.findById(id).orElse(null);
        Map<String, Object> profile = new HashMap<>(profileStatsService.buildProfileStats(id, userId));

        String relationshipStatus = friendshipService.getRelationshipStatus(userId, id);
        profile.put("success", true);
        profile.put("relationshipStatus", relationshipStatus);

        return ResponseEntity.ok(profile);
    }

    /**
     * Lấy tất cả notifications (friend requests, achievements, system notifications)
     */
    @GetMapping("/api/notifications")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getNotifications(HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "User not found"));
        }

        // Mark achievements as read
        List<AchievementNotification> unreadAchievements = achievementNotificationRepository.findUnreadByUserId(userId);
        for (AchievementNotification notif : unreadAchievements) {
            notif.setRead(true);
        }
        achievementNotificationRepository.saveAll(unreadAchievements);

        // Update last seen system notification
        user.setLastSystemNotificationSeenAt(LocalDateTime.now());
        userAccountRepository.save(user);

        // Get pending friend requests
        List<Friendship> pendingRequests = friendshipService.getPendingRequests(userId);
        List<FriendRequestView> friendRequestViews = buildFriendRequestViews(pendingRequests);

        // Get other notifications
        List<AchievementNotification> achievements = achievementNotificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<SystemNotification> systemNotifications = systemNotificationRepository.findTop5ByOrderByCreatedAtDesc();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "friendRequests", friendRequestViews,
            "achievements", achievements,
            "systemNotifications", systemNotifications
        ));
    }

    /**
     * Lấy thống kê tổng hợp (index page)
     */
    @GetMapping("/api/index")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getIndex(HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        long friendCount = friendshipService.countFriends(userId);
        long pendingCount = friendshipService.getPendingRequests(userId).size();
        long sentCount = friendshipService.getSentRequests(userId).size();

        List<UserAccount> friends = friendshipService.getFriends(userId);
        List<FriendView> friendViews = friends.stream().map(this::toFriendView).toList();

        List<Friendship> pending = friendshipService.getPendingRequests(userId);
        List<FriendRequestView> pendingViews = buildFriendRequestViews(pending);

        List<Friendship> sent = friendshipService.getSentRequests(userId);
        List<SentRequestView> sentViews = buildSentRequestViews(sent);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "stats", Map.of(
                "friendCount", friendCount,
                "pendingCount", pendingCount,
                "sentCount", sentCount
            ),
            "friends", friendViews,
            "pendingRequests", pendingViews,
            "sentRequests", sentViews
        ));
    }

    // ============== HELPER METHODS ==============

    private List<FriendRequestView> buildFriendRequestViews(List<Friendship> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        var requesterIds = requests.stream()
            .map(Friendship::getRequesterId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

        var requesterMap = new HashMap<String, UserAccount>();
        for (var account : userAccountRepository.findAllById(requesterIds)) {
            requesterMap.put(account.getId(), account);
        }

        return requests.stream()
            .filter(f -> f != null)
            .map(f -> {
                UserAccount requester = requesterMap.get(f.getRequesterId());
                return new FriendRequestView(
                    f.getId(),
                    f.getRequesterId(),
                    getDisplayName(requester, f.getRequesterId()),
                    getEmail(requester),
                    getAvatarPath(requester),
                    f.getCreatedAt()
                );
            })
            .toList();
    }

    private List<SentRequestView> buildSentRequestViews(List<Friendship> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        var addresseeIds = requests.stream()
            .map(Friendship::getAddresseeId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

        var addresseeMap = new HashMap<String, UserAccount>();
        for (var account : userAccountRepository.findAllById(addresseeIds)) {
            addresseeMap.put(account.getId(), account);
        }

        return requests.stream()
            .filter(f -> f != null)
            .map(f -> {
                UserAccount addressee = addresseeMap.get(f.getAddresseeId());
                return new SentRequestView(
                    f.getId(),
                    f.getAddresseeId(),
                    getDisplayName(addressee, f.getAddresseeId()),
                    getEmail(addressee),
                    getAvatarPath(addressee),
                    f.getCreatedAt()
                );
            })
            .toList();
    }

    private FriendView toFriendView(UserAccount account) {
        if (account == null) {
            return null;
        }
        return new FriendView(
            account.getId(),
            getDisplayName(account, account.getId()),
            getEmail(account),
            getAvatarPath(account),
            account.getScore(),
            account.isOnline()
        );
    }

    private UserSearchView toUserSearchView(UserAccount account) {
        if (account == null) {
            return null;
        }
        return new UserSearchView(
            account.getId(),
            getDisplayName(account, account.getId()),
            getEmail(account),
            getAvatarPath(account)
        );
    }

    private String getDisplayName(UserAccount account, String fallback) {
        if (account == null) {
            return fallback;
        }
        if (account.getDisplayName() != null && !account.getDisplayName().isBlank()) {
            return account.getDisplayName();
        }
        if (account.getEmail() != null && !account.getEmail().isBlank()) {
            return account.getEmail();
        }
        return fallback;
    }

    private String getEmail(UserAccount account) {
        return account != null && account.getEmail() != null ? account.getEmail() : "";
    }

    private String getAvatarPath(UserAccount account) {
        if (account == null || account.getAvatarPath() == null || account.getAvatarPath().isBlank()) {
            return "/uploads/avatars/default-avatar.jpg";
        }
        return account.getAvatarPath();
    }

    private String getSessionUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute("AUTH_USER_ID");
        if (value == null) {
            return null;
        }
        String userId = String.valueOf(value).trim();
        return userId.isEmpty() ? null : userId;
    }

    private int longestCommonSubstringLength(String source, String target) {
        int[][] table = new int[source.length() + 1][target.length() + 1];
        int max = 0;
        for (int i = 1; i <= source.length(); i++) {
            for (int j = 1; j <= target.length(); j++) {
                if (source.charAt(i - 1) == target.charAt(j - 1)) {
                    table[i][j] = table[i - 1][j - 1] + 1;
                    max = Math.max(max, table[i][j]);
                }
            }
        }
        return max;
    }

    // ============== DTOs ==============

    record SendRequestDto(String email) {}
    record SendRequestByIdDto(String addresseeId) {}
    record ActionDto(Long friendshipId) {}
    record RemoveFriendDto(String friendId) {}

    record FriendView(
        String userId,
        String displayName,
        String email,
        String avatarPath,
        int score,
        boolean online
    ) {}

    record FriendRequestView(
        Long friendshipId,
        String requesterId,
        String requesterName,
        String requesterEmail,
        String requesterAvatarPath,
        LocalDateTime createdAt
    ) {}

    record SentRequestView(
        Long friendshipId,
        String addresseeId,
        String addresseeName,
        String addresseeEmail,
        String addresseeAvatarPath,
        LocalDateTime createdAt
    ) {}

    record UserSearchView(
        String userId,
        String displayName,
        String email,
        String avatarPath
    ) {}
}
