package pt.ua.tqs.ecodelivery.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.model.InventoryAction;
import pt.ua.tqs.ecodelivery.model.InventoryAssetType;
import pt.ua.tqs.ecodelivery.model.InventoryLog;
import pt.ua.tqs.ecodelivery.repository.InventoryLogRepository;

/** Service responsible for recording and querying system inventory logs. */
@Service
public class InventoryLogService {

    private static final Logger log = LoggerFactory.getLogger(InventoryLogService.class);

    private final InventoryLogRepository inventoryLogRepository;
    private final Clock clock;

    public InventoryLogService(InventoryLogRepository inventoryLogRepository, Clock clock) {
        this.inventoryLogRepository = inventoryLogRepository;
        this.clock = clock;
    }

    public InventoryLog logHubProvisioned(Hub hub) {
        String details = String.format(Locale.US, "Hub '%s' provisioned with capacity %d at coordinates (%.6f, %.6f) with boundary radius %.1fm. Address: %s",
                hub.getName(), hub.getCapacity(), hub.getLatitude(), hub.getLongitude(), hub.getBoundaryRadiusMeters(), hub.getAddress());
        log.info("[INVENTORY] {}", details);

        InventoryLog entry = new InventoryLog(
                LocalDateTime.now(clock),
                InventoryAssetType.HUB,
                hub.getId(),
                hub.getName(),
                InventoryAction.PROVISIONED,
                details
        );
        return inventoryLogRepository.save(entry);
    }

    public InventoryLog logBikeProvisioned(Bike bike) {
        String hubInfo = bike.getCurrentHub() != null
                ? "stationed at hub '" + bike.getCurrentHub().getName() + "' (ID: " + bike.getCurrentHub().getId() + ")"
                : "no initial hub assigned";
        String details = String.format(Locale.US, "E-Bike registered with serial '%s', model '%s', cargo capacity %.1fkg, initial battery %d%%, %s",
                bike.getSerialNumber(), bike.getModel(), bike.getCargoCapacityKg(), bike.getBatteryLevel(), hubInfo);
        log.info("[INVENTORY] {}", details);

        InventoryLog entry = new InventoryLog(
                LocalDateTime.now(clock),
                InventoryAssetType.E_BIKE,
                bike.getId(),
                bike.getSerialNumber(),
                InventoryAction.PROVISIONED,
                details
        );
        return inventoryLogRepository.save(entry);
    }

    public List<InventoryLog> getAllLogs() {
        return inventoryLogRepository.findAllByOrderByTimestampDesc();
    }

    public List<InventoryLog> getLogsByAssetType(InventoryAssetType assetType) {
        return inventoryLogRepository.findByAssetTypeOrderByTimestampDesc(assetType);
    }
}
