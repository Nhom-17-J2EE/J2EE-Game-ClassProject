package com.game.hub.games.goldminer.controller;

import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GoldMinerControllerTest {

    @Test
    void indexShouldRenderGoldMinerTemplateWithCatalogModel() {
        GoldMinerController controller = new GoldMinerController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index(model);

        assertEquals("goldminer/index", view);
        assertNotNull(model.getAttribute("game"));
        assertNotNull(model.getAttribute("allGames"));
    }
}
