package pt.ua.tqs.ecodelivery.controller;

import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.InventoryLogResponse;
import pt.ua.tqs.ecodelivery.model.InventoryAssetType;
import pt.ua.tqs.ecodelivery.model.InventoryLog;
import pt.ua.tqs.ecodelivery.service.InventoryLogService;

/** REST boundary for system inventory logs. */
@RestController
@RequestMapping("/api/inventory/logs")
@CrossOrigin(origins = "*")
public class InventoryLogController {

    private final InventoryLogService inventoryLogService;

    public InventoryLogController(InventoryLogService inventoryLogService) {
        this.inventoryLogService = inventoryLogService;
    }

    @GetMapping
    public List<InventoryLogResponse> getLogs(@RequestParam(required = false) InventoryAssetType type) {
        List<InventoryLog> logs = (type != null)
                ? inventoryLogService.getLogsByAssetType(type)
                : inventoryLogService.getAllLogs();
        return logs.stream().map(log -> new InventoryLogResponse(
                log.getId(),
                log.getTimestamp(),
                log.getAssetType(),
                log.getAssetId(),
                log.getAssetIdentifier(),
                log.getAction(),
                log.getDetails()
        )).toList();
    }
}
