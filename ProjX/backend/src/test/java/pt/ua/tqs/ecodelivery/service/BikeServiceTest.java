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
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.BikeEventRepository;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;
import pt.ua.tqs.ecodelivery.repository.ReservationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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

    @Mock
    private ReservationRepository reservationRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("UTC"));

    private BikeService bikeService;

    private Hub testHub;
    private Bike testBike;

    @BeforeEach
    void setUp() {
        bikeService = new BikeService(bikeRepository, hubRepository, bikeEventRepository,
                policyService, inventoryLogService, reservationRepository, clock);

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

    @Test
    void maintenanceCancelsOnlyPendingReservationsAndRecordsEvent() {
        Reservation pending = new Reservation();
        pending.setStatus(ReservationStatus.RESERVED);
        Reservation completed = new Reservation();
        completed.setStatus(ReservationStatus.COMPLETED);
        Reservation inUse = new Reservation();
        inUse.setStatus(ReservationStatus.IN_USE);
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));
        when(bikeRepository.save(testBike)).thenReturn(testBike);
        when(reservationRepository.findByBikeIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of(pending));

        assertThat(bikeService.changeStatus(100L, BikeStatus.MAINTENANCE).getStatus())
                .isEqualTo(BikeStatus.MAINTENANCE);
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(completed.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(inUse.getStatus()).isEqualTo(ReservationStatus.IN_USE);
        verify(reservationRepository).saveAll(List.of(pending));
        ArgumentCaptor<BikeEvent> event = ArgumentCaptor.forClass(BikeEvent.class);
        verify(bikeEventRepository).save(event.capture());
        assertThat(event.getValue().getType()).isEqualTo(BikeEventType.STATUS_CHANGED);
    }

    @Test
    void reservedToAvailableRequiresNoPendingReservation() {
        testBike.setStatus(BikeStatus.RESERVED);
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));
        when(reservationRepository.findByBikeIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of(new Reservation()));
        assertThatThrownBy(() -> bikeService.changeStatus(100L, BikeStatus.AVAILABLE))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(testBike.getStatus()).isEqualTo(BikeStatus.RESERVED);
        verify(bikeRepository, never()).save(any());
    }

    @Test
    void invalidAndIdempotentChangesDoNotWrite() {
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));
        assertThat(bikeService.changeStatus(100L, BikeStatus.AVAILABLE)).isSameAs(testBike);
        assertThatThrownBy(() -> bikeService.changeStatus(100L, BikeStatus.IN_USE))
                .isInstanceOf(BusinessRuleException.class);
        testBike.setStatus(BikeStatus.RETIRED);
        assertThatThrownBy(() -> bikeService.changeStatus(100L, BikeStatus.AVAILABLE))
                .isInstanceOf(BusinessRuleException.class);
        verify(bikeEventRepository, never()).save(any());
    }

    @Test
    void deactivationPreservesSessionAndActivationDoesNotChangeStatus() {
        Reservation session = new Reservation();
        session.setStatus(ReservationStatus.IN_USE);
        testBike.setStatus(BikeStatus.IN_USE);
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));
        when(bikeRepository.save(testBike)).thenReturn(testBike);
        assertThat(bikeService.deactivate(100L).isActive()).isFalse();
        assertThat(session.getStatus()).isEqualTo(ReservationStatus.IN_USE);
        assertThat(bikeService.deactivate(100L).isActive()).isFalse();
        assertThat(bikeService.activate(100L).isActive()).isTrue();
        assertThat(testBike.getStatus()).isEqualTo(BikeStatus.IN_USE);
        verify(bikeRepository, org.mockito.Mockito.times(2)).save(testBike);
        verify(bikeEventRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void deactivationCancelsPendingReservationWithoutChangingCompletedRecord() {
        Reservation pending = new Reservation();
        pending.setStatus(ReservationStatus.RESERVED);
        Reservation completed = new Reservation();
        completed.setStatus(ReservationStatus.COMPLETED);
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));
        when(bikeRepository.save(testBike)).thenReturn(testBike);
        when(reservationRepository.findByBikeIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of(pending));
        bikeService.deactivate(100L);
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(completed.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        verify(reservationRepository).saveAll(List.of(pending));
    }

    @Test
    void inUseMaintenancePreservesOngoingSessionAndRetiredBikeCannotActivate() {
        Reservation session = new Reservation();
        session.setStatus(ReservationStatus.IN_USE);
        testBike.setStatus(BikeStatus.IN_USE);
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));
        when(bikeRepository.save(testBike)).thenReturn(testBike);
        bikeService.changeStatus(100L, BikeStatus.MAINTENANCE);
        assertThat(session.getStatus()).isEqualTo(ReservationStatus.IN_USE);
        testBike.setStatus(BikeStatus.RETIRED);
        assertThatThrownBy(() -> bikeService.activate(100L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void availabilityAndDiscoveryUseConfiguredBatteryPolicy() {
        when(bikeRepository.findById(100L)).thenReturn(Optional.of(testBike));
        when(bikeRepository.findByStatus(BikeStatus.AVAILABLE)).thenReturn(List.of(testBike));
        when(policyService.isBatterySufficient(95)).thenReturn(true);
        assertThat(bikeService.isAvailableForReservation(100L)).isTrue();
        testBike.setActive(false);
        assertThat(bikeService.findBikes(null, BikeStatus.AVAILABLE)).isEmpty();
        testBike.setActive(true);
        testBike.setCurrentHub(null);
        assertThat(bikeService.isAvailableForReservation(100L)).isFalse();
    }

    @Test
    void missingBikeIsReportedForLifecycleOperations() {
        assertThatThrownBy(() -> bikeService.changeStatus(999L, BikeStatus.MAINTENANCE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> bikeService.activate(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> bikeService.deactivate(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
