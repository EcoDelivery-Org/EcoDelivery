package pt.ua.tqs.ecodelivery.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.model.InventoryAction;
import pt.ua.tqs.ecodelivery.model.InventoryAssetType;
import pt.ua.tqs.ecodelivery.model.InventoryLog;
import pt.ua.tqs.ecodelivery.repository.InventoryLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryLogServiceTest {

    @Mock
    private InventoryLogRepository inventoryLogRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneId.of("UTC"));

    private InventoryLogService inventoryLogService;

    @BeforeEach
    void setUp() {
        inventoryLogService = new InventoryLogService(inventoryLogRepository, clock);
    }

    @Test
    @DisplayName("logHubProvisioned: saves an inventory log entry with hub details")
    void logHubProvisioned_savesLog() {
        Hub hub = new Hub("Hub Forum", "Cais da Fonte Nova", 40.6420, -8.6480, 20, 80.0);
        hub.setId(5L);

        when(inventoryLogRepository.save(any(InventoryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryLog saved = inventoryLogService.logHubProvisioned(hub);

        assertThat(saved).isNotNull();
        assertThat(saved.getAssetType()).isEqualTo(InventoryAssetType.HUB);
        assertThat(saved.getAssetId()).isEqualTo(5L);
        assertThat(saved.getAssetIdentifier()).isEqualTo("Hub Forum");
        assertThat(saved.getAction()).isEqualTo(InventoryAction.PROVISIONED);
        assertThat(saved.getDetails()).contains("Hub Forum").contains("80.0m");

        ArgumentCaptor<InventoryLog> captor = ArgumentCaptor.forClass(InventoryLog.class);
        verify(inventoryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAssetIdentifier()).isEqualTo("Hub Forum");
    }

    @Test
    @DisplayName("logBikeProvisioned: saves an inventory log entry with bike details")
    void logBikeProvisioned_savesLog() {
        Hub hub = new Hub("Hub Rossio", "Rossio", 40.64, -8.65, 10, 50.0);
        hub.setId(1L);
        Bike bike = new Bike("EB-200", "CargoPro", 45.0, 100, BikeStatus.AVAILABLE, true, hub);
        bike.setId(50L);

        when(inventoryLogRepository.save(any(InventoryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryLog saved = inventoryLogService.logBikeProvisioned(bike);

        assertThat(saved).isNotNull();
        assertThat(saved.getAssetType()).isEqualTo(InventoryAssetType.E_BIKE);
        assertThat(saved.getAssetId()).isEqualTo(50L);
        assertThat(saved.getAssetIdentifier()).isEqualTo("EB-200");
        assertThat(saved.getAction()).isEqualTo(InventoryAction.PROVISIONED);
        assertThat(saved.getDetails()).contains("EB-200").contains("Hub Rossio");
    }

    @Test
    @DisplayName("getAllLogs and getLogsByAssetType: queries repository")
    void queryLogs_success() {
        InventoryLog log = new InventoryLog();
        when(inventoryLogRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of(log));
        when(inventoryLogRepository.findByAssetTypeOrderByTimestampDesc(InventoryAssetType.HUB))
                .thenReturn(List.of(log));

        assertThat(inventoryLogService.getAllLogs()).hasSize(1);
        assertThat(inventoryLogService.getLogsByAssetType(InventoryAssetType.HUB)).hasSize(1);
    }
}
