package com.game.hub.controller;

import com.game.hub.service.WordFilterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin API Controller để quản lý danh sách từ ngữ bị cấm
 * Yêu cầu xác thực admin
 */
@Controller
@RequestMapping("/admin/word-filter")
public class WordFilterAdminController {
    
    private final WordFilterService wordFilterService;
    private static final Set<String> ADMIN_USERS = new HashSet<>();
    
    // TODO: Thay thế bằng database admin role check
    static {
        // Placeholder admin users
        // ADMIN_USERS.add("admin-user-id");
    }
    
    public WordFilterAdminController(WordFilterService wordFilterService) {
        this.wordFilterService = wordFilterService;
    }
    
    /**
     * Lấy danh sách từ bị cấm hiện tại
     */
    @GetMapping("/words")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBannedWords(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "Admin access required"
            ));
        }
        
        Set<String> words = wordFilterService.getBannedWords();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "count", words.size(),
            "words", words.stream().sorted().toList()
        ));
    }
    
    /**
     * Thêm từ cấm mới
     */
    @PostMapping("/words")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addBannedWord(
            @RequestBody AddWordRequest request,
            HttpServletRequest httpRequest) {
        
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "Admin access required"
            ));
        }
        
        if (request == null || request.word() == null || request.word().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Word is required"
            ));
        }
        
        String word = request.word().trim();
        wordFilterService.addBannedWord(word);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Word added successfully",
            "word", word
        ));
    }
    
    /**
     * Xóa từ khỏi danh sách cấm
     */
    @DeleteMapping("/words")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeBannedWord(
            @RequestParam String word,
            HttpServletRequest request) {
        
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "Admin access required"
            ));
        }
        
        if (word == null || word.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Word is required"
            ));
        }
        
        wordFilterService.removeBannedWord(word.trim());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Word removed successfully",
            "word", word.trim()
        ));
    }
    
    /**
     * Kiểm tra xem nội dung có chứa từ cấm không
     */
    @PostMapping("/check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkContent(
            @RequestBody CheckContentRequest request,
            HttpServletRequest httpRequest) {
        
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "Admin access required"
            ));
        }
        
        if (request == null || request.content() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Content is required"
            ));
        }
        
        String content = request.content();
        boolean hasBanned = wordFilterService.containsBannedWords(content);
        String filtered = wordFilterService.filter(content);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "hasBannedWords", hasBanned,
            "original", content,
            "filtered", filtered
        ));
    }
    
    /**
     * Kiểm tra xem người dùng có quyền admin không
     * TODO: Thay thế bằng @PreAuthorize hoặc annotation khác
     */
    private boolean isAdmin(HttpServletRequest request) {
        // Placeholder implementation
        // Thực tế cần kiểm tra SecurityContext hoặc user role
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        
        Object userIdObj = session.getAttribute("AUTH_USER_ID");
        if (userIdObj == null) {
            return false;
        }
        
        // TODO: Kiểm tra user có admin role
        // Hiện tại mọi user đã login đều có thể truy cập (không an toàn!)
        // return userHasAdminRole(userIdObj.toString());
        
        return false; // Tạm thời disabled
    }
    
    // ============== DTOs ==============
    
    record AddWordRequest(String word) {}
    
    record CheckContentRequest(String content) {}
}
