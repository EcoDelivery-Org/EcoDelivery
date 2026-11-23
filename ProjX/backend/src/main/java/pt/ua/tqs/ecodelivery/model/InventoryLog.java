package pt.ua.tqs.ecodelivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** An immutable system inventory log recording asset additions, updates, and lifecycle events. */
@Entity
@Table(name = "inventory_logs")
public class InventoryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private InventoryAssetType assetType;

    private Long assetId;

    private String assetIdentifier;

    @Enumerated(EnumType.STRING)
    private InventoryAction action;

    @Column(length = 1000)
    private String details;

    public InventoryLog() {
        // required by JPA
    }

    public InventoryLog(LocalDateTime timestamp, InventoryAssetType assetType, Long assetId,
                        String assetIdentifier, InventoryAction action, String details) {
        this.timestamp = timestamp;
        this.assetType = assetType;
        this.assetId = assetId;
        this.assetIdentifier = assetIdentifier;
        this.action = action;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public InventoryAssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(InventoryAssetType assetType) {
        this.assetType = assetType;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getAssetIdentifier() {
        return assetIdentifier;
    }

    public void setAssetIdentifier(String assetIdentifier) {
        this.assetIdentifier = assetIdentifier;
    }

    public InventoryAction getAction() {
        return action;
    }

    public void setAction(InventoryAction action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
