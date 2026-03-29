package com.game.hub.controller;

import com.game.hub.entity.Comment;
import com.game.hub.entity.Post;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.PostRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

@Controller
@RequestMapping("/")
public class HomeController {
    private final PostRepository postRepository;
    private final UserAccountRepository userAccountRepository;
    private final GameCatalogService gameCatalogService;

    public HomeController(PostRepository postRepository, UserAccountRepository userAccountRepository) {
        this(postRepository, userAccountRepository, new GameCatalogService());
    }

    @Autowired
    public HomeController(
        PostRepository postRepository,
        UserAccountRepository userAccountRepository,
        GameCatalogService gameCatalogService
    ) {
        this.postRepository = postRepository;
        this.userAccountRepository = userAccountRepository;
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping
    public String index(Model model) {
        List<GameCatalogItem> games = gameCatalogService.findAll();
        List<PostView> posts = loadPostFeed();
        model.addAttribute("message", "Caro Java (Thymeleaf) is running");
        model.addAttribute("posts", posts);
        model.addAttribute("games", games);
        model.addAttribute("recommendedGames", pickGamesByCodes(games, "monopoly", "caro", "cards", "chess", "puzzle"));
        model.addAttribute("onlineGames", filterGames(games, game -> game.supportsOnline() && !hasCode(game, "blackjack"), 6));
        model.addAttribute("quickPlayGames", pickGamesByCodes(games, "goldminer", "minesweeper", "quiz", "typing", "puzzle", "cards", "caro"));
        model.addAttribute("strategyGames", pickGamesByCodes(games, "caro", "chess", "xiangqi", "monopoly", "puzzle", "cards"));
        model.addAttribute("freshGames", pickGamesByCodes(games, "goldminer", "monopoly", "puzzle", "minesweeper", "typing", "quiz", "blackjack"));
        return "home/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> apiIndex() {
        return Map.of("message", "Caro Java backend is running", "posts", loadPostFeed());
    }

    @ResponseBody
    @PostMapping("posts")
    public Object createPost(@RequestBody CreatePostRequest request, HttpServletRequest httpRequest) {
        UserAccount user = sessionUser(httpRequest);
        if (user == null) {
            return Map.of("success", false, "error", "Login required");
        }
        String content = trimToNull(request == null ? null : request.content());
        if (content == null) {
            return Map.of("success", false, "error", "Content is required");
        }
        Post post = new Post();
        post.setAuthor(displayNameOf(user));
        post.setAuthorUserId(user.getId());
        post.setContent(content);
        post.setImagePath(trimToNull(request == null ? null : request.imagePath()));
        post.setCreatedAt(LocalDateTime.now());
        return Map.of("success", true, "post", postRepository.save(post));
    }

    @ResponseBody
    @PostMapping("posts/comment")
    public Map<String, Object> createComment(@RequestBody CreateCommentRequest request, HttpServletRequest httpRequest) {
        UserAccount user = sessionUser(httpRequest);
        if (user == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (request == null || request.postId() == null) {
            return Map.of("success", false, "error", "PostId is required");
        }
        String content = trimToNull(request.content());
        if (content == null) {
            return Map.of("success", false, "error", "Content is required");
        }
        Post post = postRepository.findByIdWithComments(request.postId()).orElse(null);
        if (post == null) {
            return Map.of("success", false, "error", "Post not found");
        }
        Comment comment = new Comment();
        comment.setAuthor(displayNameOf(user));
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setPost(post);
        List<Comment> comments = post.getComments();
        comments.add(comment);
        post.setComments(comments);
        postRepository.save(post);
        return Map.of("success", true, "postId", post.getId());
    }

    @ResponseBody
    @DeleteMapping("posts/{postId}")
    public Map<String, Object> deletePost(@PathVariable Long postId, HttpServletRequest httpRequest) {
        UserAccount user = sessionUser(httpRequest);
        if (user == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (postId == null) {
            return Map.of("success", false, "error", "PostId is required");
        }

        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return Map.of("success", false, "error", "Post not found");
        }

        String role = sessionRole(httpRequest);
        boolean isAdmin = role != null && "Admin".equalsIgnoreCase(role);
        String sessionUserId = trimToNull(user.getId());
        String postAuthorUserId = trimToNull(post.getAuthorUserId());
        String postAuthor = trimToNull(post.getAuthor());

        boolean isOwner = sessionUserId != null && sessionUserId.equals(postAuthorUserId);
        if (!isOwner && postAuthorUserId == null && postAuthor != null) {
            String currentDisplayName = trimToNull(displayNameOf(user));
            isOwner = postAuthor.equalsIgnoreCase(sessionUserId)
                || postAuthor.equalsIgnoreCase(currentDisplayName);
        }

        if (!isAdmin && !isOwner) {
            return Map.of("success", false, "error", "No permission to delete this post");
        }

        postRepository.delete(post);
        return Map.of("success", true, "postId", postId, "message", "Post deleted");
    }

    @GetMapping("multiplayer")
    public String multiplayer() {
        return "redirect:/games/caro";
    }

    @GetMapping("single-player")
    public String singlePlayer() {
        return "redirect:/game-mode/bot?game=caro";
    }

    public record CreatePostRequest(String content, String author, String imagePath) {
    }

    public record CreateCommentRequest(Long postId, String content, String author) {
    }

    private UserAccount sessionUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object authUserId = session.getAttribute("AUTH_USER_ID");
        if (authUserId == null) {
            return null;
        }
        String userId = String.valueOf(authUserId).trim();
        if (userId.isEmpty()) {
            return null;
        }
        return userAccountRepository.findById(userId).orElse(null);
    }

    private String sessionRole(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object authRole = session.getAttribute("AUTH_ROLE");
        if (authRole == null) {
            return null;
        }
        String role = String.valueOf(authRole).trim();
        return role.isEmpty() ? null : role;
    }

    private List<PostView> loadPostFeed() {
        return postRepository.findAllWithComments().stream()
            .map(this::toPostView)
            .toList();
    }

    private PostView toPostView(Post post) {
        List<CommentView> comments = (post.getComments() == null ? List.<Comment>of() : post.getComments()).stream()
            .sorted(Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(comment -> new CommentView(
                comment.getId(),
                comment.getAuthor(),
                comment.getContent(),
                comment.getCreatedAt()
            ))
            .toList();
        return new PostView(
            post.getId(),
            post.getContent(),
            post.getAuthor(),
            post.getAuthorUserId(),
            post.getCreatedAt(),
            post.getImagePath(),
            comments
        );
    }

    private String displayNameOf(UserAccount user) {
        String displayName = trimToNull(user.getDisplayName());
        return displayName == null ? user.getId() : displayName;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<GameCatalogItem> filterGames(
        List<GameCatalogItem> games,
        Predicate<GameCatalogItem> predicate,
        int limit
    ) {
        return games.stream()
            .filter(predicate)
            .limit(limit)
            .toList();
    }

    private List<GameCatalogItem> pickGamesByCodes(List<GameCatalogItem> games, String... codes) {
        Map<String, GameCatalogItem> byCode = new LinkedHashMap<>();
        games.forEach(game -> byCode.put(normalizeCode(game.code()), game));

        List<GameCatalogItem> ordered = new ArrayList<>();
        for (String code : codes) {
            GameCatalogItem item = byCode.get(normalizeCode(code));
            if (item != null) {
                ordered.add(item);
            }
        }
        return ordered;
    }

    private boolean hasCode(GameCatalogItem game, String code) {
        return normalizeCode(game == null ? null : game.code()).equals(normalizeCode(code));
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record PostView(
        Long id,
        String content,
        String author,
        String authorUserId,
        LocalDateTime createdAt,
        String imagePath,
        List<CommentView> comments
    ) {
    }

    public record CommentView(
        Long id,
        String author,
        String content,
        LocalDateTime createdAt
    ) {
    }
}
