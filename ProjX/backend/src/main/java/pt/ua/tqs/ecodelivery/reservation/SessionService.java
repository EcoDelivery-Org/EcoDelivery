package pt.ua.tqs.ecodelivery.reservation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
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
import pt.ua.tqs.ecodelivery.repository.HubRepository;
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
    private final HubRepository hubRepository;
    private final PolicyService policyService;
    private final BillingService billingService;
    private final HubPort hubPort;
    private final Clock clock;

    /** Kept for backwards compatibility; {@link #endSession} needs the hub repository of the other constructor. */
    public SessionService(ReservationRepository reservationRepository, CourierRepository courierRepository,
                          BikeRepository bikeRepository, PolicyService policyService,
                          BillingService billingService, HubPort hubPort, Clock clock) {
        this(reservationRepository, courierRepository, bikeRepository, null, policyService, billingService,
                hubPort, clock);
    }

    @Autowired
    public SessionService(ReservationRepository reservationRepository, CourierRepository courierRepository,
                          BikeRepository bikeRepository, HubRepository hubRepository, PolicyService policyService,
                          BillingService billingService, HubPort hubPort, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.courierRepository = courierRepository;
        this.bikeRepository = bikeRepository;
        this.hubRepository = hubRepository;
        this.policyService = policyService;
        this.billingService = billingService;
        this.hubPort = hubPort;
        this.clock = clock;
    }

    /**
     * US09: Reserves a bike for a courier, placing a temporal hold on an available e-bike.
     * Locks the vehicle state (RESERVED) for the configured policy window.
     */
    @Transactional
    public Reservation reserve(Long courierId, Long bikeId, LocalDateTime start, LocalDateTime end) {
        if (courierId == null) {
            throw new IllegalArgumentException("Courier id must not be null");
        }
        if (bikeId == null) {
            throw new IllegalArgumentException("Bike id must not be null");
        }

        // Return any expired holds to active availability before creating a new reservation
        expireHolds();

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
        if (start == null) {
            start = now;
        }
        if (end == null) {
            end = start.plus(policyService.getHoldDuration());
        }

        if (start.isBefore(now.minusSeconds(60))) {
            throw new BusinessRuleException("Reservation start time cannot be in the past");
        }

        policyService.validateReservationWindow(start, end);

        if (reservationRepository.existsConflictingReservation(bikeId, start, end)) {
            throw new BusinessRuleException("Bike " + bikeId + " has a conflicting reservation");
        }

        // Lock vehicle state for the configured policy window
        bike.setStatus(BikeStatus.RESERVED);
        bikeRepository.save(bike);

        Reservation reservation = new Reservation();
        reservation.setCourier(courier);
        reservation.setBike(bike);
        reservation.setStartHub(bike.getCurrentHub());
        reservation.setPlannedStart(start);
        reservation.setPlannedEnd(end);
        reservation.setStatus(ReservationStatus.RESERVED);

        return reservationRepository.save(reservation);
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

    /** Ends a session at a hub: bike becomes AVAILABLE there, overtime is billed and the deposit refunded. */
    @Transactional
    public Reservation endSession(Long reservationId, Long endHubId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation " + reservationId + " not found"));
        if (reservation.getStatus() != ReservationStatus.IN_USE) {
            throw new BusinessRuleException("Reservation " + reservationId + " is not in use (" + reservation.getStatus() + ")");
        }
        Hub endHub = hubRepository.findById(endHubId)
                .orElseThrow(() -> new ResourceNotFoundException("Hub " + endHubId + " not found"));
        if (bikeRepository.countByCurrentHubId(endHubId) >= endHub.getCapacity()) {
            throw new BusinessRuleException("Hub '" + endHub.getName() + "' is at maximum capacity (" + endHub.getCapacity() + ")");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        reservation.setEndHub(endHub);
        reservation.setActualEnd(now);
        reservation.setStatus(ReservationStatus.COMPLETED);

        Bike bike = reservation.getBike();
        bike.setCurrentHub(endHub);
        bike.setStatus(BikeStatus.AVAILABLE);
        bikeRepository.save(bike);
        Reservation saved = reservationRepository.save(reservation);

        billingService.chargeOvertime(saved);
        if (policyService.isDepositRequired()) {
            billingService.refundDeposit(saved);
        }
        return saved;
    }

    /**
     * US09: Returns expired holds automatically to active availability.
     * Identifies reservations in RESERVED status where plannedEnd is past or the start window elapsed,
     * updates their status to EXPIRED and unlocks the vehicle back to AVAILABLE.
     */
    @Transactional
    public List<Reservation> expireHolds() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minus(policyService.getStartWindowTolerance());
        List<Reservation> expiredReservations = reservationRepository.findExpiredReservations(now, cutoff);
        for (Reservation r : expiredReservations) {
            r.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(r);

            Bike bike = r.getBike();
            if (bike != null && bike.getStatus() == BikeStatus.RESERVED) {
                bike.setStatus(BikeStatus.AVAILABLE);
                bikeRepository.save(bike);
            }
        }
        return expiredReservations;
    }

    /** Periodic scheduled trigger to clean up expired holds automatically. */
    @org.springframework.scheduling.annotation.Scheduled(fixedRateString = "${ecodelivery.policy.hold-expiration-interval:10000}")
    public void scheduledExpireHolds() {
        expireHolds();
    }

    /** Cancels a reservation: bike becomes AVAILABLE, deposit is refunded. */
    @Transactional
    public Reservation cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation " + reservationId + " not found"));

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessRuleException("Reservation " + reservationId + " cannot be cancelled (" + reservation.getStatus() + ")");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        Bike bike = reservation.getBike();
        if (bike != null && bike.getStatus() == BikeStatus.RESERVED) {
            bike.setStatus(BikeStatus.AVAILABLE);
            bikeRepository.save(bike);
        }

        return reservationRepository.save(reservation);
    }

    public Reservation getReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation " + reservationId + " not found"));
    }

    /** Lists reservations, optionally filtered by courier and/or status; newest first. */
    public List<Reservation> findReservations(Long courierId, ReservationStatus status) {
        List<Reservation> result = courierId != null ? findByCourier(courierId)
                : reservationRepository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "plannedStart"));
        return status == null ? result : result.stream().filter(r -> r.getStatus() == status).toList();
    }

    public List<Reservation> findByCourier(Long courierId) {
        courierRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Courier " + courierId + " not found"));
        return reservationRepository.findByCourierIdOrderByPlannedStartDesc(courierId);
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
