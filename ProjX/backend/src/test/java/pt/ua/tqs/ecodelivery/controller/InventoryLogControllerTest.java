package pt.ua.tqs.ecodelivery.controller;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.tqs.ecodelivery.model.InventoryAction;
import pt.ua.tqs.ecodelivery.model.InventoryAssetType;
import pt.ua.tqs.ecodelivery.model.InventoryLog;
import pt.ua.tqs.ecodelivery.service.InventoryLogService;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryLogController.class)
class InventoryLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryLogService inventoryLogService;

    @Test
    @DisplayName("GET /api/inventory/logs - returns all inventory logs")
    void getLogs_returnsAll() throws Exception {
        InventoryLog log = new InventoryLog(
                LocalDateTime.of(2026, 11, 20, 10, 0),
                InventoryAssetType.E_BIKE,
                101L,
                "EB-101",
                InventoryAction.PROVISIONED,
                "E-Bike registered with serial 'EB-101'"
        );

        when(inventoryLogService.getAllLogs()).thenReturn(List.of(log));

        mockMvc.perform(get("/api/inventory/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].assetType", is("E_BIKE")))
                .andExpect(jsonPath("$[0].assetIdentifier", is("EB-101")))
                .andExpect(jsonPath("$[0].action", is("PROVISIONED")));
    }

    @Test
    @DisplayName("GET /api/inventory/logs?type=HUB - filters by asset type")
    void getLogs_filteredByType() throws Exception {
        InventoryLog log = new InventoryLog(
                LocalDateTime.of(2026, 11, 20, 10, 0),
                InventoryAssetType.HUB,
                1L,
                "Hub Central",
                InventoryAction.PROVISIONED,
                "Hub provisioned"
        );

        when(inventoryLogService.getLogsByAssetType(InventoryAssetType.HUB)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/inventory/logs").param("type", "HUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].assetType", is("HUB")));
    }
}
