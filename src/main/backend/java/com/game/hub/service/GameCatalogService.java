package com.game.hub.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class GameCatalogService {
    private final ExternalGameModuleService externalGameModuleService;
    private final GamePreviewMediaResolver previewMediaResolver;

    public GameCatalogService() {
        this(null, new GamePreviewMediaResolver());
    }

    public GameCatalogService(ExternalGameModuleService externalGameModuleService) {
        this(externalGameModuleService, new GamePreviewMediaResolver());
    }

    @Autowired
    public GameCatalogService(ExternalGameModuleService externalGameModuleService,
                              GamePreviewMediaResolver previewMediaResolver) {
        this.externalGameModuleService = externalGameModuleService;
        this.previewMediaResolver = previewMediaResolver;
    }

    public List<GameCatalogItem> findAll() {
        LinkedHashMap<String, GameCatalogItem> merged = new LinkedHashMap<>();
        nativeItems().forEach(item -> merged.put(normalize(item.code()), item));
        externalItems().forEach(item -> {
            String key = normalize(item.code());
            if (key.isBlank()) {
                return;
            }
            if (item.overrideExisting() || !merged.containsKey(key)) {
                merged.put(key, item);
            }
        });
        return merged.values().stream()
            .map(this::ensurePreviewMedia)
            .toList();
    }

    public Optional<GameCatalogItem> findByCode(String code) {
        return findAll().stream()
            .filter(item -> normalize(item.code()).equals(normalize(code)))
            .findFirst();
    }

    private List<GameCatalogItem> externalItems() {
        if (externalGameModuleService == null) {
            return List.of();
        }
        return externalGameModuleService.listCatalogItems();
    }

    private List<GameCatalogItem> nativeItems() {
        return List.of(
            new GameCatalogItem(
                "caro",
                "Caro",
                "Caro",
                "Caro da ho tro online room, offline 2 nguoi, bot Easy/Hard va guest.",
                "bi-grid-3x3-gap-fill",
                true,
                true,
                true,
                true,
                "Vao Caro",
                "/games/caro",
                List.of(
                    "Lobby + room invite + ws realtime.",
                    "Bot Easy/Hard va lich su tran dau.",
                    "Guest mode va profile dang nhap."
                )
            ),
            new GameCatalogItem(
                "chess",
                "Co vua",
                "Chess",
                "Co vua da co online room 1v1, bot Easy/Hard va offline 2 nguoi cung may.",
                "bi-activity",
                true,
                true,
                true,
                true,
                "Mo Co vua",
                "/games/chess",
                List.of(
                    "Ban co 8x8 va setup quan co day du.",
                    "Bot Easy/Hard va che do offline local.",
                    "Phong online va dong bo nuoc di realtime."
                )
            ),
            new GameCatalogItem(
                "xiangqi",
                "Co tuong",
                "Xiangqi",
                "Co tuong da co online room, bot Easy/Hard va offline 2 nguoi cung may.",
                "bi-diagram-3-fill",
                true,
                true,
                true,
                true,
                "Mo Co tuong",
                "/games/xiangqi",
                List.of(
                    "Bot Easy/Hard da san sang.",
                    "Offline 2 nguoi cung may.",
                    "Online room va dong bo nuoc di realtime."
                )
            ),
            new GameCatalogItem(
                "minesweeper",
                "Do min",
                "Minesweeper",
                "Minesweeper offline voi beginner/intermediate/expert, co cam co va first-click safe.",
                "bi-asterisk",
                true,
                false,
                true,
                true,
                "Mo Minesweeper",
                "/games/minesweeper",
                List.of(
                    "3 muc do beginner/intermediate/expert.",
                    "Cam co va first-click safe.",
                    "Cap nhat achievement khi thang."
                )
            ),
            new GameCatalogItem(
                "goldminer",
                "Dao vang",
                "Gold Miner",
                "Dao vang browser voi moc quay, TNT, 3 vong muc tieu diem va nang cap giua cac vong.",
                "bi-gem",
                true,
                false,
                true,
                true,
                "Choi Dao vang",
                "/goldminer",
                List.of(
                    "Moc quay, tha day va keo ve theo trong luong vat pham.",
                    "Vang, da, kim cuong, tui bi an va TNT de cat lo.",
                    "Qua 3 vong voi shop nang cap nho giua moi vong."
                )
            ),
            new GameCatalogItem(
                "cards",
                "Danh bai",
                "Cards",
                "Module Cards da co Tien Len online 4 nguoi, bot Easy/Hard va them mode Blackjack.",
                "bi-suit-spade-fill",
                true,
                true,
                true,
                true,
                "Mo Cards hub",
                "/games/cards",
                List.of(
                    "Tien Len online 4 nguoi + room realtime.",
                    "Tien Len bot Easy/Hard va bo luat mo rong.",
                    "Blackjack voi bot dealer da co route rieng."
                )
            ),
            new GameCatalogItem(
                "blackjack",
                "Blackjack",
                "Blackjack",
                "Blackjack da co room realtime, dealer bot va ban local PvP cung may.",
                "bi-suit-club-fill",
                true,
                true,
                true,
                true,
                "Mo Blackjack",
                "/games/cards/blackjack",
                List.of(
                    "Tao/join/spectate room blackjack.",
                    "Ban local 2-4 nguoi cung may voi dealer.",
                    "Dat cuoc + hit/stand/double theo luat co ban."
                )
            ),
            new GameCatalogItem(
                "quiz",
                "Quiz",
                "Quiz",
                "Quiz da co room realtime, luyen tap local va dau bot Easy/Hard.",
                "bi-patch-question-fill",
                true,
                true,
                true,
                true,
                "Mo Quiz",
                "/games/quiz",
                List.of(
                    "Tao/join/spectate room quiz.",
                    "Nhieu dang cau hoi: single, multiple, typed.",
                    "Luyen tap local va dau bot theo nhac cau hoi.",
                    "Theo doi high score."
                )
            ),
            new GameCatalogItem(
                "typing",
                "Typing Battle",
                "Typing",
                "Typing Battle da co room realtime, che do practice local va race voi bot.",
                "bi-keyboard-fill",
                true,
                true,
                true,
                true,
                "Mo Typing Battle",
                "/games/typing",
                List.of(
                    "Tao/join room typing realtime.",
                    "Theo doi progress + accuracy cua tung nguoi choi.",
                    "Practice local va race voi bot Easy/Hard.",
                    "Thong bao winner khi ket thuc."
                )
            ),
            new GameCatalogItem(
                "puzzle",
                "Puzzles",
                "Puzzle",
                "Puzzle pack gom Jigsaw, Sliding, Word Puzzle va Sudoku.",
                "bi-puzzle-fill",
                true,
                false,
                true,
                true,
                "Mo Puzzle Pack",
                "/games/puzzle",
                List.of(
                    "Jigsaw puzzle.",
                    "Sliding puzzle.",
                    "Word puzzle va Sudoku."
                )
            ),
            new GameCatalogItem(
                "monopoly",
                "Co ty phu",
                "Monopoly",
                "Co ty phu da choi duoc local 2-4 nguoi va room mode MVP, voi board 40 o, chance/community, nha-hotel, the chap va bang xep hang tai san realtime.",
                "bi-bank",
                true,
                false,
                true,
                true,
                "Mo Co ty phu",
                "/games/monopoly",
                List.of(
                    "Chuyen roll/buy/rent/jail sang backend-authoritative day du cho room mode.",
                    "Bo sung trade, auction va mortgage flow day du giua nhieu nguoi choi.",
                    "Them save/load, reconnect va bot cho che do Monopoly."
                )
            )
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private GameCatalogItem ensurePreviewMedia(GameCatalogItem item) {
        if (item == null) {
            return null;
        }
        if (item.hasPreviewMedia()) {
            return item.withPreviewMedia(
                item.previewMediaUrl(),
                previewMediaResolver.resolveMediaKind(item.previewMediaUrl(), item.previewMediaKind())
            );
        }
        return previewMediaResolver.resolveCatalogMedia(item.code())
            .map(media -> item.withPreviewMedia(media.url(), media.kind()))
            .orElse(item);
    }
}
