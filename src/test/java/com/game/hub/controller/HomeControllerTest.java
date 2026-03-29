package com.game.hub.controller;

import com.game.hub.entity.Post;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.PostRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.GameCatalogItem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeControllerTest {

    @Test
    void indexShouldPromoteMonopolyOnHomeRecommendedStrip() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(postRepository.findAllWithComments()).thenReturn(List.of());

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("home/index", controller.index(model));
        @SuppressWarnings("unchecked")
        List<GameCatalogItem> recommendedGames = (List<GameCatalogItem>) model.getAttribute("recommendedGames");
        assertTrue(recommendedGames != null && !recommendedGames.isEmpty());
        assertEquals("monopoly", recommendedGames.get(0).code());
    }

    @Test
    void indexShouldSurfaceGoldMinerInQuickPlayLane() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(postRepository.findAllWithComments()).thenReturn(List.of());

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.index(model);

        @SuppressWarnings("unchecked")
        List<GameCatalogItem> quickPlayGames = (List<GameCatalogItem>) model.getAttribute("quickPlayGames");
        assertTrue(quickPlayGames != null && quickPlayGames.stream().anyMatch(game -> "goldminer".equals(game.code())));
    }

    @Test
    void multiplayerShouldRedirectToCanonicalCaroModePage() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HomeController controller = new HomeController(postRepository, userAccountRepository);

        assertEquals("redirect:/games/caro", controller.multiplayer());
    }

    @Test
    void singlePlayerShouldRedirectToCanonicalCaroBotPicker() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HomeController controller = new HomeController(postRepository, userAccountRepository);

        assertEquals("redirect:/game-mode/bot?game=caro", controller.singlePlayer());
    }

    @Test
    void createPostShouldRequireLoginSession() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        Object result = controller.createPost(new HomeController.CreatePostRequest("hello", "spoof", ""), request);

        assertTrue(result instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) result).get("success"));
        assertEquals("Login required", ((Map<?, ?>) result).get("error"));
    }

    @Test
    void createPostShouldUseSessionUserDisplayNameInsteadOfClientAuthor() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");

        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setDisplayName("Alice");
        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        Object result = controller.createPost(new HomeController.CreatePostRequest("  Xin chao  ", "Spoofed", ""), request);

        assertTrue(result instanceof Map<?, ?>);
        assertTrue((Boolean) ((Map<?, ?>) result).get("success"));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertEquals("Alice", saved.getAuthor());
        assertEquals("Xin chao", saved.getContent());
    }

    @Test
    void createCommentShouldRequireLoginSession() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        Map<String, Object> result = controller.createComment(new HomeController.CreateCommentRequest(1L, "Hi", "spoof"), request);

        assertFalse(result.get("success") instanceof Boolean b && b);
        assertEquals("Login required", result.get("error"));
    }
}
