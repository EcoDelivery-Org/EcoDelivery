package pt.ua.tqs.ecodelivery.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.tqs.ecodelivery.dto.BikeRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeEvent;
import pt.ua.tqs.ecodelivery.model.BikeEventType;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.BikeEventRepository;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BikeServiceTest {

    @Mock
    private BikeRepository bikeRepository;

    @Mock
    private HubRepository hubRepository;

    @Mock
    private BikeEventRepository bikeEventRepository;

    @Mock
    private PolicyService policyService;

    @Mock
    private InventoryLogService inventoryLogService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("UTC"));

    private BikeService bikeService;

    private Hub testHub;
    private Bike testBike;

    @BeforeEach
    void setUp() {
        bikeService = new BikeService(bikeRepository, hubRepository, bikeEventRepository,
                policyService, inventoryLogService, clock);

        testHub = new Hub("Hub Central", "Av. Dr. Lourenço Peixinho", 40.6405, -8.6538, 5, 50.0);
        testHub.setId(10L);

        testBike = new Bike("EB-100", "CargoMax 500", 60.0, 95, BikeStatus.AVAILABLE, true, testHub);
        testBike.setId(100L);
    }

    @Test
    @DisplayName("registerBike: successfully registers a new bike with hardware attributes and assigned hub (US01)")
    void registerBike_success() {
        BikeRequest request = new BikeRequest("EB-101", "CargoMax Pro", 55.0, 10L, 100, BikeStatus.AVAILABLE);

        when(bikeRepository.existsBySerialNumber("EB-101")).thenReturn(false);
        when(hubRepository.findById(10L)).thenReturn(Optional.of(testHub));
        when(bikeRepository.countByCurrentHubId(10L)).thenReturn(2L);
        when(bikeRepository.save(any(Bike.class))).thenAnswer(invocation -> {
            Bike b = invocation.getArgument(0);
            b.setId(101L);
            return b;
        });

        Bike registered = bikeService.registerBike(request);

        assertThat(registered).isNotNull();
        assertThat(registered.getSerialNumber()).isEqualTo("EB-101");
        assertThat(registered.getModel()).isEqualTo("CargoMax Pro");
        assertThat(registered.getCargoCapacityKg()).isEqualTo(55.0);
        assertThat(registered.getBatteryLevel()).isEqualTo(100);
        assertThat(registered.getStatus()).isEqualTo(BikeStatus.AVAILABLE);
        assertThat(registered.isActive()).isTrue();
        assertThat(registered.getCurrentHub()).isEqualTo(testHub);

        // Verify event and inventory logging
        ArgumentCaptor<BikeEvent> eventCaptor = ArgumentCaptor.forClass(BikeEvent.class);
        verify(bikeEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(BikeEventType.PROVISIONED);
        assertThat(eventCaptor.getValue().getDetails()).contains("EB-101");

        verify(inventoryLogService).logBikeProvisioned(registered);
    }

    @Test
    @DisplayName("registerBike: throws BusinessRuleException when serial number is duplicated (US01)")
    void registerBike_duplicateSerial_throwsException() {
        BikeRequest request = new BikeRequest("EB-100", "Model", 40.0, 10L);
        when(bikeRepository.existsBySerialNumber("EB-100")).thenReturn(true);

        assertThatThrownBy(() -> bikeService.registerBike(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("registerBike: throws BusinessRuleException when serial number is blank (US01)")
    void registerBike_blankSerial_throwsException() {
        BikeRequest request = new BikeRequest("  ", "Model", 40.0, 10L);

        assertThatThrownBy(() -> bikeService.registerBike(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("serial number cannot be empty");
    }

    @Test
    @DisplayName("registerBike: throws BusinessRuleException when cargo capacity is non-positive (US01)")
    void registerBike_invalidCapacity_throwsException() {
        BikeRequest request = new BikeRequest("EB-102", "Model", 0.0, 10L);

        assertThatThrownBy(() -> bikeService.registerBike(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cargo capacity must be greater than zero");
    }

    @Test
    @DisplayName("registerBike: throws ResourceNotFoundException when hubId does not exist (US01)")
    void registerBike_hubNotFound_throwsException() {
        BikeRequest request = new BikeRequest("EB-103", "Model", 50.0, 999L);
        when(bikeRepository.existsBySerialNumber("EB-103")).thenReturn(false);
        when(hubRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bikeService.registerBike(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hub not found with id: 999");
    }

    @Test
    @DisplayName("registerBike: throws BusinessRuleException when hub is at maximum capacity (US01)")
    void registerBike_hubAtCapacity_throwsException() {
        BikeRequest request = new BikeRequest("EB-104", "Model", 50.0, 10L);
        when(bikeRepository.existsBySerialNumber("EB-104")).thenReturn(false);
        when(hubRepository.findById(10L)).thenReturn(Optional.of(testHub));
        when(bikeRepository.countByCurrentHubId(10L)).thenReturn(5L); // capacity is 5!

        assertThatThrownBy(() -> bikeService.registerBike(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum capacity");
    }

    @Test
    @DisplayName("findBikes and getBike: retrieves bike inventory (US01)")
    void findBikesAndGetBike_success() {
        when(bikeRepository.findAll()).thenReturn(List.of(testBike));
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));

        assertThat(bikeService.findBikes(null, null)).hasSize(1);
        assertThat(bikeService.getBike(100L).getSerialNumber()).isEqualTo("EB-100");
    }
}
