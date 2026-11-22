package pt.ua.tqs.ecodelivery.reservation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.DepositFailedException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.model.Payment;
import pt.ua.tqs.ecodelivery.model.PaymentStatus;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;
import pt.ua.tqs.ecodelivery.payment.BillingService;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.port.HubPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;
import pt.ua.tqs.ecodelivery.repository.ReservationRepository;

/** Business logic for reservations and usage sessions, from booking to the end of use. */
@Service
public class SessionService {

    /** DEC-4: reservations in these states count as active. */
    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE);

    private final ReservationRepository reservationRepository;
    private final CourierRepository courierRepository;
    private final BikeRepository bikeRepository;
    private final PolicyService policyService;
    private final BillingService billingService;
    private final HubPort hubPort;
    private final Clock clock;

    public SessionService(ReservationRepository reservationRepository, CourierRepository courierRepository,
                          BikeRepository bikeRepository, PolicyService policyService,
                          BillingService billingService, HubPort hubPort, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.courierRepository = courierRepository;
        this.bikeRepository = bikeRepository;
        this.policyService = policyService;
        this.billingService = billingService;
        this.hubPort = hubPort;
        this.clock = clock;
    }

    /** Reserves a bike for a courier, for a period. */
    public Reservation reserve(Long courierId, Long bikeId, LocalDateTime start, LocalDateTime end) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * US 10, path B: starts using a bike immediately, without a prior reservation.
     *
     * <p>All checks run first; then the deposit is charged (BR8); only if it succeeds does the
     * session start and the hub release the bike. A failed deposit is not rolled back, so it stays
     * recorded, and the attempt is kept as a CANCELLED reservation.
     */
    @Transactional(noRollbackFor = DepositFailedException.class)
    public Reservation startImmediateUse(Long courierId, Long bikeId) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Courier " + courierId + " not found"));
        Bike bike = bikeRepository.findById(bikeId)
                .orElseThrow(() -> new ResourceNotFoundException("Bike " + bikeId + " not found"));

        checkCourierIsEligible(courier);
        long active = reservationRepository.countByCourierIdAndStatusIn(courierId, ACTIVE_STATUSES);
        if (!policyService.canCourierReserve(courierId, active)) {
            throw new BusinessRuleException("Courier " + courierId + " already has an active reservation or session");
        }
        checkBikeCanBeUsed(bike);
        if (bike.getStatus() != BikeStatus.AVAILABLE) {
            throw new BusinessRuleException("Bike " + bikeId + " is not available (" + bike.getStatus() + ")");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime plannedEnd = now.plus(policyService.getMaxReservationDuration());
        if (reservationRepository.existsConflictingReservation(bikeId, now, plannedEnd)) {
            throw new BusinessRuleException("Bike " + bikeId + " is reserved before " + plannedEnd);
        }

        Reservation session = new Reservation();
        session.setCourier(courier);
        session.setBike(bike);
        session.setStartHub(bike.getCurrentHub());
        session.setPlannedStart(now);
        session.setPlannedEnd(plannedEnd);
        session.setStatus(ReservationStatus.RESERVED);
        session = reservationRepository.save(session);

        return start(session, bike, now, true);
    }

    /**
     * US 10, path A: the courier starts the usage session of his reservation.
     *
     * <p>All checks run first; then the deposit is charged (BR8); only if it succeeds does the
     * session start and the hub release the bike. A failed deposit is not rolled back, so it stays
     * recorded; the reservation itself is left unchanged.
     */
    @Transactional(noRollbackFor = DepositFailedException.class)
    public Reservation startSession(Long reservationId, Long courierId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation " + reservationId + " not found"));

        Courier courier = reservation.getCourier();
        if (courier == null || !Objects.equals(courier.getId(), courierId)) {
            throw new BusinessRuleException("Reservation " + reservationId + " does not belong to courier " + courierId);
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessRuleException("Reservation " + reservationId + " cannot be started (" + reservation.getStatus() + ")");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!policyService.isWithinStartWindow(reservation.getPlannedStart(), now)) {
            throw new BusinessRuleException("Reservation " + reservationId + " can only be started around " + reservation.getPlannedStart());
        }
        checkCourierIsEligible(courier);

        Bike bike = reservation.getBike();
        checkBikeCanBeUsed(bike);
        if (bike.getStatus() != BikeStatus.AVAILABLE && bike.getStatus() != BikeStatus.RESERVED) {
            throw new BusinessRuleException("Bike " + bike.getId() + " is not available (" + bike.getStatus() + ")");
        }

        return start(reservation, bike, now, false);
    }

    /** Ends a session at a hub: bike becomes AVAILABLE there, overtime is billed. */
    public Reservation endSession(Long reservationId, Long endHubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Cancels a reservation: bike becomes AVAILABLE, deposit is refunded. */
    public Reservation cancel(Long reservationId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Reservation getReservation(Long reservationId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<Reservation> findByCourier(Long courierId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** BR7/DEC-8: the courier must belong to a registered company. */
    private void checkCourierIsEligible(Courier courier) {
        if (!policyService.isCourierEligible(courier)) {
            throw new BusinessRuleException("Courier " + courier.getId() + " is not eligible to use the fleet");
        }
    }

    /** BR2, BR1 and PROP-2: the bike must be in service, charged enough and parked at a hub. */
    private void checkBikeCanBeUsed(Bike bike) {
        if (!bike.isActive() || bike.getStatus() == BikeStatus.MAINTENANCE || bike.getStatus() == BikeStatus.RETIRED) {
            throw new BusinessRuleException("Bike " + bike.getId() + " is out of service");
        }
        if (!policyService.isBatterySufficient(bike.getBatteryLevel())) {
            throw new BusinessRuleException("Bike " + bike.getId() + " has " + bike.getBatteryLevel()
                    + " % battery; the minimum is " + policyService.getMinBatteryLevel() + " %");
        }
        if (bike.getCurrentHub() == null) {
            throw new BusinessRuleException("Bike " + bike.getId() + " is not parked at a hub");
        }
    }

    /**
     * Deposit (BR8), then the state changes, then the hub releases the bike.
     *
     * @param immediateUse whether the reservation was created just now for an immediate use; if its
     *                     deposit fails, that attempt is kept as CANCELLED so it blocks nothing
     */
    private Reservation start(Reservation reservation, Bike bike, LocalDateTime now, boolean immediateUse) {
        if (policyService.isDepositRequired()) {
            Payment deposit = billingService.chargeDeposit(reservation);
            if (deposit == null || deposit.getStatus() != PaymentStatus.SUCCEEDED) {
                if (immediateUse) {
                    reservation.setStatus(ReservationStatus.CANCELLED);
                    reservationRepository.save(reservation);
                }
                throw new DepositFailedException("The deposit for reservation " + reservation.getId() + " was refused");
            }
        }

        Hub hub = bike.getCurrentHub();
        reservation.setStatus(ReservationStatus.IN_USE);
        reservation.setActualStart(now);
        bike.setStatus(BikeStatus.IN_USE);
        bike.setCurrentHub(null);
        Reservation started = reservationRepository.save(reservation);
        bikeRepository.save(bike);

        hubPort.releaseBike(hub.getId(), bike.getId());
        return started;
    }
}
