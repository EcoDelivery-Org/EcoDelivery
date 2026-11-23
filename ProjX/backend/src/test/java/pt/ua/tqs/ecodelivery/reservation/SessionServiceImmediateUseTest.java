package pt.ua.tqs.ecodelivery.reservation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.BIKE_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.COURIER_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.DEPOSIT;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.HUB_ID;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.MAX_DURATION;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.NOW;
import static pt.ua.tqs.ecodelivery.reservation.StartFixtures.NO_DEPOSIT;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
 * US 10, path B: a courier starts using a bike immediately, without a prior reservation.
 *
 * <p>Same set-up as {@link SessionServiceStartReservationTest}: plain unit test, mocks for the
 * repositories, billing and the hub service, a fixed clock and the real {@code PolicyService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessionService — immediate use (US 10, path B)")
class SessionServiceImmediateUseTest {

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

    @BeforeEach
    void setUp() {
        hub = StartFixtures.hub();
        courier = StartFixtures.courier(COURIER_ID, StartFixtures.company());
        bike = StartFixtures.bike(hub, BikeStatus.AVAILABLE, 80, true);

        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(courier));
        when(bikeRepository.findById(BIKE_ID)).thenReturn(Optional.of(bike));
        when(reservationRepository.existsConflictingReservation(eq(BIKE_ID), any(), any())).thenReturn(false);
        when(reservationRepository.countByCourierIdAndStatusIn(eq(COURIER_ID), anyCollection())).thenReturn(0L);
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

    /** A refused immediate use must leave the bike where it was and start no session. */
    private void assertNothingChanged(BikeStatus bikeStatusBefore) {
        assertAll(
                () -> assertEquals(bikeStatusBefore, bike.getStatus(), "bike status"),
                () -> assertSame(hub, bike.getCurrentHub(), "the bike must still be at its hub"));
        verify(hubPort, never()).releaseBike(any(), any());
        verify(bikeRepository, never()).save(any());
        assertNoSessionWasSaved();
    }

    /** No reservation may have been saved in the IN_USE state. */
    private void assertNoSessionWasSaved() {
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository, atLeast(0)).save(saved.capture());
        assertTrue(saved.getAllValues().stream().noneMatch(r -> r.getStatus() == ReservationStatus.IN_USE),
                "no usage session may have been saved");
    }

    // ---------------------------------------------------------------- BR4: authorized immediate use

    @Test
    @DisplayName("BR4: an authorized immediate use starts a session and the hub releases the bike")
    void authorizedImmediateUseStartsTheSession() {
        Reservation session = serviceAt(NOW).startImmediateUse(COURIER_ID, BIKE_ID);

        assertAll(
                () -> assertSame(courier, session.getCourier(), "courier"),
                () -> assertSame(bike, session.getBike(), "bike"),
                () -> assertSame(hub, session.getStartHub(), "start hub"),
                () -> assertEquals(ReservationStatus.IN_USE, session.getStatus(), "session status"),
                () -> assertEquals(NOW, session.getActualStart(), "start of the session"),
                () -> assertEquals(NOW, session.getPlannedStart(), "planned start"),
                () -> assertEquals(NOW.plus(MAX_DURATION), session.getPlannedEnd(),
                        "planned end = now + maximum duration configured by the client"),
                () -> assertEquals(BikeStatus.IN_USE, bike.getStatus(), "bike status"),
                () -> assertNull(bike.getCurrentHub(), "the bike has left its hub"));
        verify(billingService).chargeDeposit(any(Reservation.class));
        verify(hubPort).releaseBike(HUB_ID, BIKE_ID);
        verify(bikeRepository).save(bike);
    }

    @Test
    @DisplayName("BR4: an unknown bike cannot be used")
    void unknownBikeCannotBeUsed() {
        SessionService service = serviceAt(NOW);

        assertThrows(ResourceNotFoundException.class, () -> service.startImmediateUse(COURIER_ID, 999L));

        verify(hubPort, never()).releaseBike(any(), any());
        verify(billingService, never()).chargeDeposit(any());
    }

    @Test
    @DisplayName("BR4: an unknown courier cannot use a bike")
    void unknownCourierCannotUseABike() {
        SessionService service = serviceAt(NOW);

        assertThrows(ResourceNotFoundException.class, () -> service.startImmediateUse(999L, BIKE_ID));

        assertNothingChanged(BikeStatus.AVAILABLE);
        verify(billingService, never()).chargeDeposit(any());
    }

    @ParameterizedTest(name = "bike {0} is refused")
    @EnumSource(value = BikeStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("BR4/BR2: only an AVAILABLE bike can be used immediately")
    void onlyAvailableBikesCanBeUsedImmediately(BikeStatus status) {
        bike.setStatus(status);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        assertNothingChanged(status);
        verify(billingService, never()).chargeDeposit(any());
    }

    // ---------------------------------------------------------------- BR3 / DEC-6: future reservations

    @Test
    @DisplayName("BR3/DEC-6: immediate use is refused when the bike is reserved before now + maximum duration")
    void immediateUseRefusedWhenAReservationStartsWithinTheMaximumDuration() {
        when(reservationRepository.existsConflictingReservation(eq(BIKE_ID), any(), any())).thenReturn(true);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        assertNothingChanged(BikeStatus.AVAILABLE);
        verify(billingService, never()).chargeDeposit(any());
    }

    @Test
    @DisplayName("BR3/DEC-6: the conflict check covers [now, now + maximum duration configured by the client]")
    void conflictCheckUsesTheClientsMaximumDuration() {
        serviceAt(NOW).startImmediateUse(COURIER_ID, BIKE_ID);

        verify(reservationRepository).existsConflictingReservation(BIKE_ID, NOW, NOW.plus(MAX_DURATION));
    }

    // ---------------------------------------------------------------- DEC-4: one active per courier

    @Test
    @DisplayName("DEC-4: a courier who already has an active reservation or session cannot use another bike")
    void courierWithAnActiveReservationIsRefused() {
        when(reservationRepository.countByCourierIdAndStatusIn(eq(COURIER_ID), anyCollection())).thenReturn(1L);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        assertNothingChanged(BikeStatus.AVAILABLE);
        verify(billingService, never()).chargeDeposit(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("DEC-4: both reservations (RESERVED) and sessions (IN_USE) count as active")
    void reservedAndInUseCountAsActive() {
        serviceAt(NOW).startImmediateUse(COURIER_ID, BIKE_ID);

        ArgumentCaptor<Collection<ReservationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(reservationRepository).countByCourierIdAndStatusIn(eq(COURIER_ID), statuses.capture());
        assertTrue(statuses.getValue().contains(ReservationStatus.RESERVED), "RESERVED counts as active");
        assertTrue(statuses.getValue().contains(ReservationStatus.IN_USE), "IN_USE counts as active");
    }

    // ---------------------------------------------------------------- BR1: battery

    @ParameterizedTest(name = "battery {0} % is refused")
    @ValueSource(ints = {0, 19})
    @DisplayName("BR1: a bike with less than 20 % battery cannot be used")
    void lowBatteryIsRefused(int batteryLevel) {
        bike.setBatteryLevel(batteryLevel);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        assertNothingChanged(BikeStatus.AVAILABLE);
        verify(billingService, never()).chargeDeposit(any());
    }

    @ParameterizedTest(name = "battery {0} % is accepted")
    @ValueSource(ints = {20, 100})
    @DisplayName("BR1: a bike with 20 % battery or more can be used")
    void sufficientBatteryIsAccepted(int batteryLevel) {
        bike.setBatteryLevel(batteryLevel);

        Reservation session = serviceAt(NOW).startImmediateUse(COURIER_ID, BIKE_ID);

        assertEquals(ReservationStatus.IN_USE, session.getStatus());
    }

    // ---------------------------------------------------------------- BR2: deactivated

    @Test
    @DisplayName("BR2: a deactivated bike cannot be used")
    void deactivatedBikeIsRefused() {
        bike.setActive(false);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        assertNothingChanged(BikeStatus.AVAILABLE);
        verify(billingService, never()).chargeDeposit(any());
    }

    @Test
    @DisplayName("PROP-2: a bike that is not parked at a hub cannot be used")
    void bikeNotAtAHubIsRefused() {
        bike.setCurrentHub(null);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        verify(hubPort, never()).releaseBike(any(), any());
        verify(billingService, never()).chargeDeposit(any());
        assertNoSessionWasSaved();
    }

    // ---------------------------------------------------------------- BR7: eligibility

    @Test
    @DisplayName("BR7/DEC-8: a courier without a registered company cannot use a bike")
    void courierWithoutCompanyIsRefused() {
        courier.setCompany(null);
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        assertNothingChanged(BikeStatus.AVAILABLE);
        verify(billingService, never()).chargeDeposit(any());
    }

    // ---------------------------------------------------------------- BR8: deposit

    @Test
    @DisplayName("BR8: if the deposit fails, no session starts and the bike is not released")
    void failedDepositPreventsTheSession() {
        when(billingService.chargeDeposit(any(Reservation.class)))
                .thenReturn(StartFixtures.deposit(PaymentStatus.FAILED));
        SessionService service = serviceAt(NOW);

        assertThrows(BusinessRuleException.class, () -> service.startImmediateUse(COURIER_ID, BIKE_ID));

        verify(billingService).chargeDeposit(any(Reservation.class));
        assertNothingChanged(BikeStatus.AVAILABLE);
    }

    @Test
    @DisplayName("BR8: when no deposit is required, the session starts without charging one")
    void noDepositChargedWhenNotRequired() {
        Reservation session = serviceAt(NOW, NO_DEPOSIT).startImmediateUse(COURIER_ID, BIKE_ID);

        assertEquals(ReservationStatus.IN_USE, session.getStatus());
        verify(billingService, never()).chargeDeposit(any());
        verify(hubPort).releaseBike(HUB_ID, BIKE_ID);
    }
}
