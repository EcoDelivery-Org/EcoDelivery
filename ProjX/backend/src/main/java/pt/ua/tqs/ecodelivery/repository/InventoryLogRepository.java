package pt.ua.tqs.ecodelivery.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.InventoryAssetType;
import pt.ua.tqs.ecodelivery.model.InventoryLog;

/** Data access for system inventory audit logs. */
@Repository
public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
    List<InventoryLog> findAllByOrderByTimestampDesc();
    List<InventoryLog> findByAssetTypeOrderByTimestampDesc(InventoryAssetType assetType);
}
