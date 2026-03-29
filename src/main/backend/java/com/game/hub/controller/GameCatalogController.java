package com.game.hub.controller;

import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Locale;

@Controller
@RequestMapping("/games")
public class GameCatalogController {
    private final GameCatalogService gameCatalogService;

    public GameCatalogController(GameCatalogService gameCatalogService) {
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("games", gameCatalogService.findAll());
        return "games/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> apiIndex() {
        return Map.of("games", gameCatalogService.findAll());
    }

    @GetMapping("/{code}/rooms")
    public String rooms(@PathVariable String code,
                        @RequestParam(required = false) String roomId) {
        GameCatalogItem game = gameCatalogService.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        if (game.isExternalSource()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "External game module uses its own module page or external API gateway"
            );
        }

        return switch (normalizeCode(game.code())) {
            case "cards" -> redirectToDedicatedRoom("/cards/tien-len/rooms", "/cards/tien-len/room/", roomId);
            case "blackjack" -> redirectToDedicatedRoom("/games/cards/blackjack", "/games/cards/blackjack/room/", roomId);
            case "quiz" -> redirectToDedicatedRoom("/games/quiz", "/games/quiz/room/", roomId);
            case "typing" -> redirectToDedicatedRoom("/games/typing", "/games/typing/room/", roomId);
            case "monopoly" -> redirectToDedicatedRoom("/games/monopoly", "/games/monopoly/room/", roomId);
            case "goldminer" -> "redirect:/games/goldminer";
            default -> routeSharedRoom(game.code(), roomId);
        };
    }

    @GetMapping("/{code}")
    public String detail(@PathVariable String code, Model model) {
        GameCatalogItem game = gameCatalogService.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
        if ("monopoly".equals(game.code())) {
            model.addAttribute("defaultRoomId", "");
            model.addAttribute("roomPage", false);
            model.addAttribute("localPage", false);
        }
        if (game.usesExternalDetailView()) {
            return "games/external-detail";
        }
        return switch (game.code()) {
            case "caro" -> "games/caro";
            case "chess" -> "games/chess";
            case "xiangqi" -> "games/xiangqi";
            case "minesweeper" -> "games/minesweeper";
            case "cards" -> "games/cards";
            case "blackjack" -> "games/cards/blackjack";
            case "quiz" -> "games/quiz";
            case "typing" -> "games/typing";
            case "puzzle" -> "games/puzzle/index";
            case "monopoly" -> "games/monopoly";
            default -> "games/detail";
        };
    }

    private String forwardToSharedRoomHub(String gameCode, String roomId) {
        StringBuilder forward = new StringBuilder("forward:/online-hub?game=")
            .append(UriUtils.encodeQueryParam(gameCode, StandardCharsets.UTF_8));
        String normalizedRoomId = normalizeRoomId(roomId);
        if (!normalizedRoomId.isEmpty()) {
            forward.append("&roomId=")
                .append(UriUtils.encodeQueryParam(normalizedRoomId, StandardCharsets.UTF_8));
        }
        return forward.toString();
    }

    private String routeSharedRoom(String gameCode, String roomId) {
        String normalizedRoomId = normalizeRoomId(roomId);
        String sharedRoomPathPrefix = sharedRoomPathPrefix(gameCode);
        if (!normalizedRoomId.isEmpty() && !sharedRoomPathPrefix.isEmpty()) {
            return "redirect:" + sharedRoomPathPrefix
                + UriUtils.encodePathSegment(normalizedRoomId, StandardCharsets.UTF_8);
        }
        return forwardToSharedRoomHub(gameCode, normalizedRoomId);
    }

    private String redirectWithRoomQuery(String basePath, String roomParamName, String roomId) {
        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId.isEmpty()) {
            return "redirect:" + basePath;
        }
        return "redirect:" + basePath
            + "?"
            + UriUtils.encodeQueryParam(roomParamName, StandardCharsets.UTF_8)
            + "="
            + UriUtils.encodeQueryParam(normalizedRoomId, StandardCharsets.UTF_8);
    }

    private String redirectToDedicatedRoom(String lobbyPath, String roomPathPrefix, String roomId) {
        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId.isEmpty()) {
            return "redirect:" + lobbyPath;
        }
        return "redirect:" + roomPathPrefix
            + UriUtils.encodePathSegment(normalizedRoomId, StandardCharsets.UTF_8);
    }

    private String sharedRoomPathPrefix(String gameCode) {
        return switch (normalizeCode(gameCode)) {
            case "caro" -> "/game/room/";
            case "chess" -> "/chess/online/room/";
            case "xiangqi" -> "/xiangqi/online/room/";
            default -> "";
        };
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRoomId(String value) {
        return value == null ? "" : value.trim();
    }
}
