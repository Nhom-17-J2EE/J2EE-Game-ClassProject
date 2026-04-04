package com.game.hub.games.goldminer.controller;

import com.game.hub.games.goldminer.service.GoldminerService;
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
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * REST API Controller cho Goldminer Game
 */
@Controller
@RequestMapping("/games/goldminer")
public class GoldminerController {
    
    private final GoldminerService goldminerService;
    
    public GoldminerController(GoldminerService goldminerService) {
        this.goldminerService = goldminerService;
    }
    
    /**
     * Trang chính Goldminer
     */
    @GetMapping
    public String index(HttpServletRequest request, Model model) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return "redirect:/account/login-page";
        }
        model.addAttribute("currentUserId", userId);
        return "games/goldminer/index";
    }
    
    /**
     * Trang room Goldminer
     */
    @GetMapping("/room/{roomId}")
    public String room(@PathVariable String roomId, HttpServletRequest request, Model model) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return "redirect:/account/login-page";
        }
        model.addAttribute("currentUserId", userId);
        model.addAttribute("roomId", roomId);
        return "games/goldminer/room";
    }
    
    /**
     * API: Lấy trạng thái room
     */
    @GetMapping("/api/room/{roomId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRoomState(@PathVariable String roomId, HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        
        Map<String, Object> state = goldminerService.getRoomState(roomId);
        return ResponseEntity.ok(Map.of("success", true, "data", state));
    }
    
    /**
     * API: Tạo room mới
     */
    @PostMapping("/api/create-room")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createRoom(HttpServletRequest request) {
        String userId = getSessionUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        
        String roomId = "goldminer_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        goldminerService.getOrCreateRoom(roomId);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "roomId", roomId
        ));
    }
    
    /**
     * API: Bắt đầu trò chơi
     */
    @PostMapping("/api/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startGame(@RequestBody StartGameRequest request, HttpServletRequest httpRequest) {
        String userId = getSessionUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        
        if (request == null || request.roomId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "roomId is required"));
        }
        
        goldminerService.startGame(request.roomId(), userId);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Game started"
        ));
    }
    
    /**
     * API: Dừng trò chơi
     */
    @PostMapping("/api/stop")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> stopGame(@RequestBody StopGameRequest request, HttpServletRequest httpRequest) {
        String userId = getSessionUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        
        if (request == null || request.roomId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "roomId is required"));
        }
        
        goldminerService.stopGame(request.roomId());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Game stopped"
        ));
    }
    
    /**
     * Lấy userId từ session
     */
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
    
    // ============== DTOs ==============
    
    record StartGameRequest(String roomId) {}
    record StopGameRequest(String roomId) {}
}
