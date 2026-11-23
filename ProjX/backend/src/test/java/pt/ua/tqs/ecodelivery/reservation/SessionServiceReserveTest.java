package pt.ua.tqs.ecodelivery.reservation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.BIKE_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.COURIER_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.DEPOSIT;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.NOW;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.RESERVATION_ID;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;
import pt.ua.tqs.ecodelivery.payment.BillingService;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.port.HubPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;
import pt.ua.tqs.ecodelivery.repository.ReservationRepository;

/**
 * US09: Vehicle Reservation unit tests.
 * Acceptance Criteria:
 * - Reservation locks vehicle state for the configured policy window.
 * - Expired holds automatically return the vehicle to active availability.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessionService — Vehicle Reservation and Temporal Hold (US09)")
class SessionServiceReserveTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private CourierRepository courierRepository;
    @Mock
    private BikeRepository bikeRepository;
    @Mock
    private BillingService billingService;
    @Mock
    private HubPort hubPort;

    private Hub hub;
    private Courier courier;
    private Bike bike;
    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        hub = StartFixtures.hub();
        courier = StartFixtures.courier(COURIER_ID, StartFixtures.company());
        bike = StartFixtures.bike(hub, BikeStatus.AVAILABLE, 80, true);
        policyService = StartFixtures.policy(DEPOSIT);

        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(courier));
        when(bikeRepository.findById(BIKE_ID)).thenReturn(Optional.of(bike));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(returnsFirstArg());
        when(bikeRepository.save(any(Bike.class))).thenAnswer(returnsFirstArg());
    }

    private SessionService serviceAt(LocalDateTime now) {
        return new SessionService(reservationRepository, courierRepository, bikeRepository,
                policyService, billingService, hubPort, StartFixtures.clockAt(now));
    }

    // ---------------------------------------------------------------- US09: Success & Lock Vehicle State

    @Test
    @DisplayName("US09: Placing a temporal hold locks vehicle state to RESERVED for configured policy window")
    void reserve_locksVehicleStateForConfiguredWindow() {
        SessionService service = serviceAt(NOW);

        Reservation res = service.reserve(COURIER_ID, BIKE_ID, null, null);

        assertNotNull(res);
        assertEquals(ReservationStatus.RESERVED, res.getStatus());
        assertEquals(BikeStatus.RESERVED, bike.getStatus(), "Vehicle state must be locked to RESERVED");
        assertEquals(NOW, res.getPlannedStart(), "Planned start defaults to now");
        assertEquals(NOW.plusMinutes(15), res.getPlannedEnd(), "Planned end defaults to now + configured hold duration");
        assertEquals(hub, res.getStartHub());

        verify(bikeRepository).save(bike);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("US09: Placing a reservation with custom start and end within policy max duration succeeds")
    void reserve_withCustomStartAndEnd_succeeds() {
        SessionService service = serviceAt(NOW);
        LocalDateTime customStart = NOW.plusHours(1);
        LocalDateTime customEnd = NOW.plusHours(3);

        Reservation res = service.reserve(COURIER_ID, BIKE_ID, customStart, customEnd);

        assertEquals(ReservationStatus.RESERVED, res.getStatus());
        assertEquals(BikeStatus.RESERVED, bike.getStatus());
        assertEquals(customStart, res.getPlannedStart());
        assertEquals(customEnd, res.getPlannedEnd());
    }

    // ---------------------------------------------------------------- US09: Expiration & Auto Return to Availability

    @Test
    @DisplayName("US09: Expired holds automatically return the vehicle to active availability")
    void expireHolds_returnsVehicleToActiveAvailability() {
        SessionService service = serviceAt(NOW);

        // Given a reservation that expired
        Reservation expiredReservation = new Reservation();
        expiredReservation.setCourier(courier);
        expiredReservation.setBike(bike);
        bike.setStatus(BikeStatus.RESERVED);
        expiredReservation.setStatus(ReservationStatus.RESERVED);
        expiredReservation.setPlannedStart(NOW.minusMinutes(30));
        expiredReservation.setPlannedEnd(NOW.minusMinutes(15));

        when(reservationRepository.findExpiredReservations(eq(NOW), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredReservation));

        List<Reservation> processed = service.expireHolds();

        assertEquals(1, processed.size());
        assertEquals(ReservationStatus.EXPIRED, expiredReservation.getStatus());
        assertEquals(BikeStatus.AVAILABLE, bike.getStatus(), "Expired hold must return bike to AVAILABLE");

        verify(bikeRepository).save(bike);
        verify(reservationRepository).save(expiredReservation);
    }

    @Test
    @DisplayName("US09: Expire holds does not modify active non-expired reservations")
    void expireHolds_doesNotTouchActiveHolds() {
        SessionService service = serviceAt(NOW);

        when(reservationRepository.findExpiredReservations(eq(NOW), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        List<Reservation> processed = service.expireHolds();

        assertEquals(0, processed.size());
        verify(bikeRepository, never()).save(any(Bike.class));
    }

    // ---------------------------------------------------------------- US09: Courier Validations

    @Test
    @DisplayName("US09: Throws ResourceNotFoundException when courier is unknown")
    void reserve_unknownCourier_throws() {
        when(courierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> serviceAt(NOW).reserve(999L, BIKE_ID, null, null));
    }

    @Test
    @DisplayName("US09/BR7: Throws BusinessRuleException when courier has no registered company")
    void reserve_courierWithoutCompany_throws() {
        courier.setCompany(null);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, null, null));
    }

    @Test
    @DisplayName("US09/DEC-4: Throws BusinessRuleException when courier already has an active reservation")
    void reserve_courierWithActiveReservation_throws() {
        when(reservationRepository.countByCourierIdAndStatusIn(eq(COURIER_ID), any()))
                .thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, null, null));
    }

    // ---------------------------------------------------------------- US09: Bike Validations

    @Test
    @DisplayName("US09: Throws ResourceNotFoundException when bike is unknown")
    void reserve_unknownBike_throws() {
        when(bikeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> serviceAt(NOW).reserve(COURIER_ID, 999L, null, null));
    }

    @ParameterizedTest
    @EnumSource(value = BikeStatus.class, names = {"RESERVED", "IN_USE", "MAINTENANCE", "RETIRED"})
    @DisplayName("US09: Throws BusinessRuleException when bike status is not AVAILABLE")
    void reserve_bikeNotAvailable_throws(BikeStatus status) {
        bike.setStatus(status);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, null, null));
    }

    @Test
    @DisplayName("US09/BR2: Throws BusinessRuleException when bike is deactivated")
    void reserve_deactivatedBike_throws() {
        bike.setActive(false);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, null, null));
    }

    @Test
    @DisplayName("US09/BR1: Throws BusinessRuleException when bike battery is below minimum (20%)")
    void reserve_lowBatteryBike_throws() {
        bike.setBatteryLevel(19);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, null, null));
    }

    @Test
    @DisplayName("US09/PROP-2: Throws BusinessRuleException when bike is not parked at a hub")
    void reserve_bikeNotAtHub_throws() {
        bike.setCurrentHub(null);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, null, null));
    }

    @Test
    @DisplayName("US09/BR3: Throws BusinessRuleException when overlapping reservation exists")
    void reserve_conflictingReservation_throws() {
        when(reservationRepository.existsConflictingReservation(eq(BIKE_ID), any(), any()))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, null, null));
    }

    @Test
    @DisplayName("US09: Throws BusinessRuleException when start time is in the past")
    void reserve_startInThePast_throws() {
        LocalDateTime past = NOW.minusMinutes(10);
        LocalDateTime end = NOW.plusHours(1);

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, past, end));
    }

    @Test
    @DisplayName("US09: Throws BusinessRuleException when reservation window exceeds max duration")
    void reserve_exceedsMaxDuration_throws() {
        LocalDateTime start = NOW;
        LocalDateTime end = NOW.plusHours(7); // Max duration is 6 hours

        assertThrows(BusinessRuleException.class, () -> serviceAt(NOW).reserve(COURIER_ID, BIKE_ID, start, end));
    }

    // ---------------------------------------------------------------- US09: Cancellation

    @Test
    @DisplayName("US09: Courier cancels reservation — status becomes CANCELLED and bike returns to AVAILABLE")
    void cancel_success() {
        SessionService service = serviceAt(NOW);
        Reservation res = StartFixtures.reservation(courier, bike, NOW, ReservationStatus.RESERVED);
        bike.setStatus(BikeStatus.RESERVED);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(res));

        Reservation cancelled = service.cancel(RESERVATION_ID);

        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
        assertEquals(BikeStatus.AVAILABLE, bike.getStatus(), "Cancelled hold returns bike to AVAILABLE");
        verify(bikeRepository).save(bike);
        verify(reservationRepository).save(res);
    }

    @Test
    @DisplayName("US09: Cancellation fails if reservation is not in RESERVED status")
    void cancel_notReserved_throws() {
        SessionService service = serviceAt(NOW);
        Reservation res = StartFixtures.reservation(courier, bike, NOW, ReservationStatus.IN_USE);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(res));

        assertThrows(BusinessRuleException.class, () -> service.cancel(RESERVATION_ID));
    }

    @Test
    @DisplayName("US09: getReservation returns reservation or throws 404")
    void getReservation_returnsReservation() {
        SessionService service = serviceAt(NOW);
        Reservation res = StartFixtures.reservation(courier, bike, NOW, ReservationStatus.RESERVED);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(res));

        assertEquals(res, service.getReservation(RESERVATION_ID));
        assertThrows(ResourceNotFoundException.class, () -> service.getReservation(999L));
    }

    @Test
    @DisplayName("US09: findByCourier returns courier's reservations")
    void findByCourier_returnsReservations() {
        SessionService service = serviceAt(NOW);
        Reservation res = StartFixtures.reservation(courier, bike, NOW, ReservationStatus.RESERVED);
        when(reservationRepository.findByCourierIdOrderByPlannedStartDesc(COURIER_ID))
                .thenReturn(List.of(res));

        List<Reservation> found = service.findByCourier(COURIER_ID);
        assertEquals(1, found.size());
        assertEquals(res, found.getFirst());
    }
}
