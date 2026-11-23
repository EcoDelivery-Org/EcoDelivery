package pt.ua.tqs.ecodelivery.dto;

import java.time.LocalDateTime;
import pt.ua.tqs.ecodelivery.model.InventoryAction;
import pt.ua.tqs.ecodelivery.model.InventoryAssetType;

/** DTO representing an inventory audit log entry. */
public record InventoryLogResponse(
        Long id,
        LocalDateTime timestamp,
        InventoryAssetType assetType,
        Long assetId,
        String assetIdentifier,
        InventoryAction action,
        String details) {
}
