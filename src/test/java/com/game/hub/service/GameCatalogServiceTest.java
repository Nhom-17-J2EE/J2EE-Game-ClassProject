package com.game.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCatalogServiceTest {

    @Test
    void shouldExposeAllGamesAddedToCatalog() {
        GameCatalogService service = new GameCatalogService();

        var games = service.findAll();
        Set<String> codes = games.stream().map(GameCatalogItem::code).collect(Collectors.toSet());

        assertEquals(11, games.size());
        assertTrue(codes.contains("caro"));
        assertTrue(codes.contains("chess"));
        assertTrue(codes.contains("xiangqi"));
        assertTrue(codes.contains("minesweeper"));
        assertTrue(codes.contains("goldminer"));
        assertTrue(codes.contains("cards"));
        assertTrue(codes.contains("blackjack"));
        assertTrue(codes.contains("quiz"));
        assertTrue(codes.contains("typing"));
        assertTrue(codes.contains("puzzle"));
        assertTrue(codes.contains("monopoly"));
        assertTrue(service.findByCode("CARO").isPresent());
        var caro = service.findByCode("caro").orElseThrow();
        assertTrue(caro.availableNow());
        assertTrue(caro.supportsOnline());
        assertTrue(caro.supportsOffline());
        assertEquals("/games/caro", caro.primaryActionUrl());

        var chess = service.findByCode("chess").orElseThrow();
        assertTrue(chess.availableNow());
        assertTrue(chess.supportsOffline());
        assertTrue(chess.supportsOnline());
        assertNotNull(chess.primaryActionUrl());
        assertEquals("/games/chess", chess.primaryActionUrl());

        var cards = service.findByCode("cards").orElseThrow();
        assertTrue(cards.availableNow());
        assertTrue(cards.supportsOnline());
        assertTrue(cards.supportsOffline());
        assertEquals("/games/cards", cards.primaryActionUrl());

        var blackjack = service.findByCode("blackjack").orElseThrow();
        assertTrue(blackjack.availableNow());
        assertTrue(blackjack.supportsOnline());
        assertTrue(blackjack.supportsOffline());
        assertEquals("/games/cards/blackjack", blackjack.primaryActionUrl());

        var xiangqi = service.findByCode("xiangqi").orElseThrow();
        assertTrue(xiangqi.availableNow());
        assertTrue(xiangqi.supportsOffline());
        assertTrue(xiangqi.supportsOnline());
        assertEquals("/games/xiangqi", xiangqi.primaryActionUrl());

        var minesweeper = service.findByCode("minesweeper").orElseThrow();
        assertTrue(minesweeper.availableNow());
        assertTrue(minesweeper.supportsOffline());
        assertTrue(minesweeper.supportsGuest());
        assertEquals(false, minesweeper.supportsOnline());
        assertEquals("/games/minesweeper", minesweeper.primaryActionUrl());

        var goldminer = service.findByCode("goldminer").orElseThrow();
        assertTrue(goldminer.availableNow());
        assertTrue(goldminer.supportsOffline());
        assertTrue(goldminer.supportsGuest());
        assertEquals(false, goldminer.supportsOnline());
        assertEquals("/goldminer", goldminer.primaryActionUrl());
        assertTrue(goldminer.hasPreviewMedia());
        assertTrue(goldminer.previewMediaIsImage());
        assertTrue(goldminer.previewMediaUrl().startsWith("/images/games/home/goldminer."));

        var quiz = service.findByCode("quiz").orElseThrow();
        assertTrue(quiz.availableNow());
        assertTrue(quiz.supportsOnline());
        assertTrue(quiz.supportsOffline());
        assertEquals("/games/quiz", quiz.primaryActionUrl());
        assertTrue(quiz.hasPreviewMedia());
        assertTrue(quiz.previewMediaIsImage());
        assertTrue(quiz.previewMediaUrl().startsWith("/images/games/home/quiz."));

        var typing = service.findByCode("typing").orElseThrow();
        assertTrue(typing.availableNow());
        assertTrue(typing.supportsOnline());
        assertTrue(typing.supportsOffline());
        assertEquals("/games/typing", typing.primaryActionUrl());

        var puzzle = service.findByCode("puzzle").orElseThrow();
        assertTrue(puzzle.availableNow());
        assertTrue(puzzle.supportsOffline());
        assertEquals(false, puzzle.supportsOnline());
        assertEquals("/games/puzzle", puzzle.primaryActionUrl());

        var monopoly = service.findByCode("monopoly").orElseThrow();
        assertTrue(monopoly.availableNow());
        assertEquals(false, monopoly.supportsOnline());
        assertTrue(monopoly.supportsOffline());
        assertTrue(monopoly.supportsGuest());
        assertEquals("/games/monopoly", monopoly.primaryActionUrl());
        assertTrue(monopoly.description().contains("room mode MVP"));
    }

    @Test
    void shouldMergeExternalModulesAndAllowOverride() throws Exception {
        Path registryFile = Files.createTempFile("external-game-modules", ".json");
        ExternalGameModuleService externalService = new ExternalGameModuleService(
            new ObjectMapper(),
            registryFile,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            HttpClient.newHttpClient()
        );
        externalService.upsertModules(List.of(
            new ExternalGameModuleConfig(
                "python-blast",
                "Python Blast",
                "Py Blast",
                "Game module viet bang Python.",
                "bi-rocket-takeoff-fill",
                true,
                true,
                false,
                true,
                "Mo module Python",
                "https://python.example.com/play",
                List.of("Embed + API"),
                "external-module",
                "python",
                "redirect",
                "",
                "https://python.example.com/api",
                "https://python.example.com/manifest.json",
                false
            ),
            new ExternalGameModuleConfig(
                "quiz",
                "Quiz API Python",
                "Quiz Py",
                "Ghi de game quiz bang module ngoai.",
                "bi-patch-question-fill",
                true,
                true,
                false,
                true,
                "Mo quiz ngoai",
                "https://python.example.com/quiz",
                List.of("Override native quiz"),
                "external-api",
                "python",
                "redirect",
                "",
                "https://python.example.com/quiz-api",
                "https://python.example.com/manifest.json",
                true
            )
        ), false);

        GameCatalogService service = new GameCatalogService(externalService);

        var externalModule = service.findByCode("python-blast").orElseThrow();
        assertTrue(externalModule.isExternalSource());
        assertEquals("python", externalModule.runtime());
        assertEquals("https://python.example.com/play", externalModule.primaryActionUrl());
        assertEquals("https://python.example.com/api", externalModule.apiBaseUrl());
        assertTrue(externalModule.previewMediaUrl().isBlank());

        var overriddenQuiz = service.findByCode("quiz").orElseThrow();
        assertTrue(overriddenQuiz.isExternalSource());
        assertEquals("Quiz API Python", overriddenQuiz.displayName());
        assertEquals("https://python.example.com/quiz", overriddenQuiz.primaryActionUrl());
    }

    @Test
    void shouldInferVideoPreviewMediaForExternalModule() throws Exception {
        Path registryFile = Files.createTempFile("external-game-modules-video", ".json");
        ExternalGameModuleService externalService = new ExternalGameModuleService(
            new ObjectMapper(),
            registryFile,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            HttpClient.newHttpClient()
        );
        externalService.upsertModules(List.of(
            new ExternalGameModuleConfig(
                "video-module",
                "Video Module",
                "Video Module",
                "Module co preview video.",
                "bi-camera-reels-fill",
                true,
                true,
                false,
                true,
                "Mo video module",
                "https://video.example.com/play",
                List.of("Preview video loop"),
                "external-module",
                "node",
                "redirect",
                "",
                "https://video.example.com/api",
                "https://video.example.com/manifest.json",
                false,
                "https://cdn.example.com/media/video-module.webm",
                ""
            )
        ), false);

        GameCatalogService service = new GameCatalogService(externalService);

        var videoModule = service.findByCode("video-module").orElseThrow();
        assertEquals("https://cdn.example.com/media/video-module.webm", videoModule.previewMediaUrl());
        assertTrue(videoModule.previewMediaIsVideo());
    }
}
