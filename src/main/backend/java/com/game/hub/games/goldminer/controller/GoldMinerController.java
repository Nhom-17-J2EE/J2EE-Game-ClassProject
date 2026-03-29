package com.game.hub.games.goldminer.controller;

import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/goldminer")
public class GoldMinerController {
    private static final String GAME_CODE = "goldminer";

    private final GameCatalogService gameCatalogService;

    public GoldMinerController() {
        this(new GameCatalogService());
    }

    @Autowired
    public GoldMinerController(GameCatalogService gameCatalogService) {
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping
    public String index(Model model) {
        if (model != null) {
            GameCatalogItem game = gameCatalogService.findByCode(GAME_CODE).orElse(null);
            model.addAttribute("game", game);
            model.addAttribute("allGames", gameCatalogService.findAll());
        }
        return "goldminer/index";
    }
}
