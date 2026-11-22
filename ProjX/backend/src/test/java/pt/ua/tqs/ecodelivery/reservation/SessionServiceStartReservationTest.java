package pt.ua.tqs.ecodelivery.reservation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.BIKE_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.COURIER_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.DEPOSIT;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.HUB_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.NOW;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.NO_DEPOSIT;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.OTHER_COURIER_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.RESERVATION_ID;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
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
import pt.ua.tqs.ecodelivery.model.PaymentStatus;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;
import pt.ua.tqs.ecodelivery.payment.BillingService;
import pt.ua.tqs.ecodelivery.port.HubPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;
import pt.ua.tqs.ecodelivery.repository.ReservationRepository;

/**
 * US 10, path A: a courier starts the usage session of his reservation.
 *
 * <p>No Spring context: the service is built with {@code new}, repositories, billing and the hub
 * service are Mockito mocks, the clock is fixed, and the real {@code PolicyService} is used (it is a
 * plain, deterministic object, so there is nothing to gain by mocking it).
 *
 * <p>Stubs are lenient because these tests specify <i>what</i> the service decides, not the exact
 * sequence of repository look-ups it does to get there.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessionService — start a reserved session (US 10, path A)")
class SessionServiceStartReservationTest {

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
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        hub = StartFixtures.hub();
        courier = StartFixtures.courier(COURIER_ID, StartFixtures.company());
        bike = StartFixtures.bike(hub, BikeStatus.RESERVED, 80, true);
        reservation = StartFixtures.reservation(courier, bike, NOW, ReservationStatus.RESERVED);

        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(courier));
        when(courierRepository.findById(OTHER_COURIER_ID))
                .thenReturn(Optional.of(StartFixtures.courier(OTHER_COURIER_ID, StartFixtures.company())));
        when(bikeRepository.findById(BIKE_ID)).thenReturn(Optional.of(bike));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(returnsFirstArg());
        when(bikeRepository.save(any(Bike.class))).thenAnswer(returnsFirstArg());
        when(billingService.chargeDeposit(any(Reservation.class)))
                .thenReturn(StartFixtures.deposit(PaymentStatus.SUCCEEDED));
    }

    private SessionService serviceAt(LocalDateTime now) {
        return serviceAt(now, DEPOSIT);
    }

    private SessionService serviceAt(LocalDateTime now, BigDecimal depositAmount) {
        return new SessionService(reservationRepository, courierRepository, bikeRepository,
                StartFixtures.policy(depositAmount), billingService, hubPort, StartFixtures.clockAt(now));
    }

    /** A refused start must leave everything as it was: no session, no bike change, no release. */
    private void assertNothingChanged(BikeStatus bikeStatusBefore) {
        assertAll(
                () -> assertEquals(ReservationStatus.RESERVED, reservation.getStatus(), "reservation status"),
                () -> assertNull(reservation.getActualStart(), "the session must not have started"),
                () -> assertEquals(bikeStatusBefore, bike.getStatus(), "bike status"),
                () -> assertSame(hub, bike.getCurrentHub(), "the bike must still be at its hub"));
        verify(hubPort, never()).releaseBike(any(), any());
        verify(reservationRepository, never()).save(any());
        verify(bikeRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- BR4: valid reservation

    @Test
    @DisplayName("BR4: a courier starts his valid reservation — the session begins and the hub releases the bike")
    void validReservationStartsTheSession() {
        Reservation started = serviceAt(NOW).startSession(RESERVATION_ID, COURIER_ID);

        assertAll(
                () -> assertEquals(ReservationStatus.IN_USE, started.getStatus(), "reservation status"),
                () -> assertEquals(NOW, started.getActualStart(), "start of the session"),
                () -> assertEquals(BikeStatus.IN_USE, bike.getStatus(), "bike status"),
                () -> assertNull(bike.getCurrentHub(), "the bike has left its hub"));
        verify(billingService).chargeDeposit(reservation);
        verify(hubPort).releaseBike(HUB_ID, BIKE_ID);
        verify(reservationRepository).save(reservation);
        verify(bikeRepository).save(bike);
    }

    @Test
    @DisplayName("BR4: an unknown reservation cannot be started")
    void unknownReservationCannotBeStarted() {
        SessionService service = serviceAt(NOW);

        assertThrows(ResourceNotFoundException.class, () -> service.startSession(999L, COURIER_ID));

        assertNothingChanged(BikeStatus.RESERVED);
        verify(billingService, never()).chargeDeposit(any());
    }

    @Test
    @DisplayName("BR4: a courier cannot start another courier's reservation")
    void anotherCouriersReservationCannotBeStarted() {
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, OTHER_COURIER_ID));

        assertNothingChanged(BikeStatus.RESERVED);
        verify(billingService, never()).chargeDeposit(any());
    }

    @ParameterizedTest(name = "reservation {0} is refused")
    @EnumSource(value = ReservationStatus.class, names = "RESERVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("BR4: only a reservation in the RESERVED state can be started")
    void onlyReservedReservationsCanBeStarted(ReservationStatus status) {
        reservation.setStatus(status);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertEquals(status, reservation.getStatus(), "the reservation must keep its state");
        verify(hubPort, never()).releaseBike(any(), any());
        verify(billingService, never()).chargeDeposit(any());
    }

    // ---------------------------------------------------------------- DEC-5: start window

    @ParameterizedTest(name = "{0} min from the planned start is accepted")
    @ValueSource(longs = {-15, 0, 15})
    @DisplayName("BR4/DEC-5: a reservation can be started from 15 min before to 15 min after its planned start")
    void reservationStartsInsideItsWindow(long minutesFromPlannedStart) {
        Reservation started = serviceAt(NOW.plusMinutes(minutesFromPlannedStart))
                .startSession(RESERVATION_ID, COURIER_ID);

        assertEquals(ReservationStatus.IN_USE, started.getStatus());
        verify(hubPort).releaseBike(HUB_ID, BIKE_ID);
    }

    @ParameterizedTest(name = "{0} min from the planned start is refused")
    @ValueSource(longs = {-16, 16, -120, 120})
    @DisplayName("BR4/DEC-5: a reservation cannot be started outside its window")
    void reservationCannotStartOutsideItsWindow(long minutesFromPlannedStart) {
        SessionService service = serviceAt(NOW.plusMinutes(minutesFromPlannedStart));

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertNothingChanged(BikeStatus.RESERVED);
        verify(billingService, never()).chargeDeposit(any());
    }

    // ---------------------------------------------------------------- BR1: battery

    @ParameterizedTest(name = "battery {0} % is refused")
    @ValueSource(ints = {0, 19})
    @DisplayName("BR1: a bike with less than 20 % battery cannot be used")
    void lowBatteryIsRefused(int batteryLevel) {
        bike.setBatteryLevel(batteryLevel);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertNothingChanged(BikeStatus.RESERVED);
        verify(billingService, never()).chargeDeposit(any());
    }

    @ParameterizedTest(name = "battery {0} % is accepted")
    @ValueSource(ints = {20, 100})
    @DisplayName("BR1: a bike with 20 % battery or more can be used")
    void sufficientBatteryIsAccepted(int batteryLevel) {
        bike.setBatteryLevel(batteryLevel);

        Reservation started = serviceAt(NOW).startSession(RESERVATION_ID, COURIER_ID);

        assertEquals(ReservationStatus.IN_USE, started.getStatus());
    }

    // ---------------------------------------------------------------- BR2: vehicle state

    @ParameterizedTest(name = "bike in {0} is refused")
    @EnumSource(value = BikeStatus.class, names = {"MAINTENANCE", "RETIRED"})
    @DisplayName("BR2: a bike under maintenance or retired cannot be used")
    void bikeInMaintenanceOrRetiredIsRefused(BikeStatus status) {
        bike.setStatus(status);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertNothingChanged(status);
        verify(billingService, never()).chargeDeposit(any());
    }

    @Test
    @DisplayName("BR2: a deactivated bike cannot be used")
    void deactivatedBikeIsRefused() {
        bike.setActive(false);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertNothingChanged(BikeStatus.RESERVED);
        verify(billingService, never()).chargeDeposit(any());
    }

    @Test
    @DisplayName("BR4: a bike already in use cannot be started again")
    void bikeAlreadyInUseIsRefused() {
        bike.setStatus(BikeStatus.IN_USE);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertNothingChanged(BikeStatus.IN_USE);
        verify(billingService, never()).chargeDeposit(any());
    }

    @ParameterizedTest(name = "bike {0} is accepted")
    @EnumSource(value = BikeStatus.class, names = {"AVAILABLE", "RESERVED"})
    @DisplayName("DEC-7: the reserved bike can be started whether it is already RESERVED or still AVAILABLE")
    void reservedBikeCanBeStartedWhetherReservedOrAvailable(BikeStatus status) {
        bike.setStatus(status);

        Reservation started = serviceAt(NOW).startSession(RESERVATION_ID, COURIER_ID);

        assertEquals(ReservationStatus.IN_USE, started.getStatus());
        assertEquals(BikeStatus.IN_USE, bike.getStatus());
    }

    @Test
    @DisplayName("PROP-2: a bike that is not parked at a hub cannot be used")
    void bikeNotAtAHubIsRefused() {
        bike.setCurrentHub(null);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertEquals(ReservationStatus.RESERVED, reservation.getStatus());
        verify(hubPort, never()).releaseBike(any(), any());
        verify(billingService, never()).chargeDeposit(any());
    }

    // ---------------------------------------------------------------- BR7: eligibility

    @Test
    @DisplayName("BR7/DEC-8: a courier without a registered company cannot start a session")
    void courierWithoutCompanyIsRefused() {
        courier.setCompany(null);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        assertNothingChanged(BikeStatus.RESERVED);
        verify(billingService, never()).chargeDeposit(any());
    }

    // ---------------------------------------------------------------- BR8: deposit

    @Test
    @DisplayName("BR8: if the deposit fails, the session does not start and the bike is not released")
    void failedDepositPreventsTheSession() {
        when(billingService.chargeDeposit(any(Reservation.class)))
                .thenReturn(StartFixtures.deposit(PaymentStatus.FAILED));
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startSession(RESERVATION_ID, COURIER_ID));

        verify(billingService).chargeDeposit(reservation);
        assertNothingChanged(BikeStatus.RESERVED);
    }

    @Test
    @DisplayName("BR8: the bike is released only after the deposit succeeded")
    void bikeIsReleasedOnlyAfterTheDeposit() {
        serviceAt(NOW).startSession(RESERVATION_ID, COURIER_ID);

        InOrder order = inOrder(billingService, hubPort);
        order.verify(billingService).chargeDeposit(reservation);
        order.verify(hubPort).releaseBike(HUB_ID, BIKE_ID);
    }

    @Test
    @DisplayName("BR8: when no deposit is required, the session starts without charging one")
    void noDepositChargedWhenNotRequired() {
        Reservation started = serviceAt(NOW, NO_DEPOSIT).startSession(RESERVATION_ID, COURIER_ID);

        assertEquals(ReservationStatus.IN_USE, started.getStatus());
        verify(billingService, never()).chargeDeposit(any());
        verify(hubPort).releaseBike(HUB_ID, BIKE_ID);
    }
}
