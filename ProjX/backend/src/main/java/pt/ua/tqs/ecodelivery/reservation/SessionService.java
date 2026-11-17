package pt.ua.tqs.ecodelivery.reservation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.payment.BillingService;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;
import pt.ua.tqs.ecodelivery.repository.ReservationRepository;
import pt.ua.tqs.ecodelivery.service.BikeService;
import pt.ua.tqs.ecodelivery.service.HubService;

/** Business logic for reservations and usage sessions, from booking to the end of use. */
@Service
public class SessionService {

    private final ReservationRepository reservationRepository;
    private final CourierRepository courierRepository;
    private final BikeService bikeService;
    private final HubService hubService;
    private final PolicyService policyService;
    private final BillingService billingService;
    private final Clock clock;

    public SessionService(ReservationRepository reservationRepository, CourierRepository courierRepository,
                          BikeService bikeService, HubService hubService, PolicyService policyService,
                          BillingService billingService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.courierRepository = courierRepository;
        this.bikeService = bikeService;
        this.hubService = hubService;
        this.policyService = policyService;
        this.billingService = billingService;
        this.clock = clock;
    }

    /** Reserves a bike for a courier, for a period. */
    public Reservation reserve(Long courierId, Long bikeId, LocalDateTime start, LocalDateTime end) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Starts using a bike immediately, without a prior reservation. */
    public Reservation startImmediateUse(Long courierId, Long bikeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Starts the session of a reservation: bike goes IN_USE, deposit is charged. */
    public Reservation startSession(Long reservationId) {
        throw new UnsupportedOperationException("Not implemented yet");
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

    /** Whether the bike already has a reservation overlapping the period. */
    private boolean hasOverlap(Long bikeId, LocalDateTime start, LocalDateTime end) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
