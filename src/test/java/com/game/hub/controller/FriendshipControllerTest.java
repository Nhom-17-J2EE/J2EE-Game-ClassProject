package com.game.hub.controller;

import com.game.hub.entity.Friendship;
import com.game.hub.entity.FriendshipStatus;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.AchievementNotificationRepository;
import com.game.hub.repository.SystemNotificationRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.FriendshipService;
import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test cases for FriendshipController
 * Tests the new REST API endpoints with ResponseEntity and JSON responses
 */
class FriendshipControllerTest {

    @Test
    void getNotificationsShouldReturnUnauthorizedWhenNotLoggedIn() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getSession(false)).thenReturn(null);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        ResponseEntity<Map<String, Object>> response = controller.getNotifications(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(String.valueOf(response.getBody().get("error")).contains("Unauthorized"));
    }

    @Test
    void getUserDetailShouldReturnNotFoundWhenUserNotExists() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(userAccountRepository.existsById("missing-id")).thenReturn(false);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        ResponseEntity<Map<String, Object>> response = controller.getUserDetail("missing-id", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(String.valueOf(response.getBody().get("error")).contains("User not found"));
    }

    @Test
    void sendRequestShouldReturnUnauthorizedWhenNotLoggedIn() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getSession(false)).thenReturn(null);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        FriendshipController.SendRequestDto dto = new FriendshipController.SendRequestDto("test@example.com");
        ResponseEntity<Map<String, Object>> response = controller.sendRequest(dto, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(String.valueOf(response.getBody().get("error")).contains("Unauthorized"));
    }

    @Test
    void acceptRequestShouldReturnSuccessWhenValid() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(friendshipService.acceptRequest(77L, "u1")).thenReturn(true);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        FriendshipController.ActionDto dto = new FriendshipController.ActionDto(77L);
        ResponseEntity<Map<String, Object>> response = controller.acceptRequest(dto, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("accepted"));
    }

    @Test
    void acceptRequestShouldReturnBadRequestWhenFails() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(friendshipService.acceptRequest(77L, "u1")).thenReturn(false);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        FriendshipController.ActionDto dto = new FriendshipController.ActionDto(77L);
        ResponseEntity<Map<String, Object>> response = controller.acceptRequest(dto, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    void getIndexShouldReturnFriendsAndPendingRequests() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");

        UserAccount current = createUser("u1", "Current User", "u1@example.com", "/uploads/avatars/u1.jpg", 10, true);
        UserAccount friend = createUser("u2", "Friend User", "u2@example.com", "/uploads/avatars/u2.jpg", 120, false);
        UserAccount requester = createUser("u3", "Requester User", "u3@example.com", "/uploads/avatars/u3.jpg", 90, true);
        UserAccount addressee = createUser("u4", "Addressee User", "u4@example.com", "/uploads/avatars/u4.jpg", 70, false);

        Friendship pending = new Friendship();
        pending.setId(11L);
        pending.setRequesterId("u3");
        pending.setAddresseeId("u1");
        pending.setStatus(FriendshipStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.now());

        Friendship sent = new Friendship();
        sent.setId(12L);
        sent.setRequesterId("u1");
        sent.setAddresseeId("u4");
        sent.setStatus(FriendshipStatus.PENDING);
        sent.setCreatedAt(LocalDateTime.now());

        when(friendshipService.getFriends("u1")).thenReturn(List.of(friend));
        when(friendshipService.countFriends("u1")).thenReturn(1L);
        when(friendshipService.getPendingRequests("u1")).thenReturn(List.of(pending));
        when(friendshipService.getSentRequests("u1")).thenReturn(List.of(sent));

        Map<String, UserAccount> usersById = new HashMap<>();
        usersById.put("u3", requester);
        usersById.put("u4", addressee);

        when(userAccountRepository.findAllById(ArgumentMatchers.any())).thenAnswer(invocation -> {
            Iterable<String> ids = invocation.getArgument(0);
            List<UserAccount> found = new ArrayList<>();
            for (String id : ids) {
                UserAccount u = usersById.get(id);
                if (u != null) {
                    found.add(u);
                }
            }
            return found;
        });

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        ResponseEntity<Map<String, Object>> response = controller.getIndex(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) response.getBody().get("stats");
        assertEquals(1L, stats.get("friendCount"));
        assertEquals(1L, stats.get("pendingCount"));
        assertEquals(1L, stats.get("sentCount"));

        @SuppressWarnings("unchecked")
        List<FriendshipController.FriendView> friends = (List<FriendshipController.FriendView>) response.getBody().get("friends");
        assertEquals(1, friends.size());
        assertEquals("u2", friends.get(0).userId());
        assertEquals("Friend User", friends.get(0).displayName());

        @SuppressWarnings("unchecked")
        List<FriendshipController.FriendRequestView> pendingRequests = (List<FriendshipController.FriendRequestView>) response.getBody().get("pendingRequests");
        assertEquals(1, pendingRequests.size());
        assertEquals(11L, pendingRequests.get(0).friendshipId());
        assertEquals("u3", pendingRequests.get(0).requesterId());

        @SuppressWarnings("unchecked")
        List<FriendshipController.SentRequestView> sentRequests = (List<FriendshipController.SentRequestView>) response.getBody().get("sentRequests");
        assertEquals(1, sentRequests.size());
        assertEquals(12L, sentRequests.get(0).friendshipId());
        assertEquals("u4", sentRequests.get(0).addresseeId());
    }

    @Test
    void declineRequestShouldReturnSuccessWhenValid() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(friendshipService.declineRequest(77L, "u1")).thenReturn(true);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        FriendshipController.ActionDto dto = new FriendshipController.ActionDto(77L);
        ResponseEntity<Map<String, Object>> response = controller.declineRequest(dto, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("declined"));
    }

    @Test
    void removeFriendShouldReturnSuccessWhenValid() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(friendshipService.removeFriendship("u1", "u2")).thenReturn(true);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        FriendshipController.RemoveFriendDto dto = new FriendshipController.RemoveFriendDto("u2");
        ResponseEntity<Map<String, Object>> response = controller.removeFriend(dto, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("removed"));
    }

    @Test
    void getFriendsShouldReturnListOfAcceptedFriends() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");

        UserAccount friend1 = createUser("u2", "Friend 1", "friend1@example.com", "/uploads/avatars/u2.jpg", 100, true);
        UserAccount friend2 = createUser("u3", "Friend 2", "friend2@example.com", "/uploads/avatars/u3.jpg", 200, false);

        when(friendshipService.getFriends("u1")).thenReturn(List.of(friend1, friend2));

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        ResponseEntity<Map<String, Object>> response = controller.getFriends(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));

        @SuppressWarnings("unchecked")
        List<FriendshipController.FriendView> friends = (List<FriendshipController.FriendView>) response.getBody().get("friends");
        assertEquals(2, friends.size());
        assertEquals("u2", friends.get(0).userId());
        assertEquals("u3", friends.get(1).userId());
    }

    @Test
    void sendRequestByIdShouldReturnSuccessWhenValid() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(friendshipService.sendRequest("u1", "u2")).thenReturn(true);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        FriendshipController.SendRequestByIdDto dto = new FriendshipController.SendRequestByIdDto("u2");
        ResponseEntity<Map<String, Object>> response = controller.sendRequestById(dto, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("sent"));
    }

    // ============== HELPER METHODS ==============

    private UserAccount createUser(String id, String displayName, String email, String avatarPath, int score, boolean online) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setAvatarPath(avatarPath);
        user.setScore(score);
        user.setOnline(online);
        return user;
    }

