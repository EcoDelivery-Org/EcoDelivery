package pt.ua.tqs.ecodelivery.reservation;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.ImmediateUseRequest;
import pt.ua.tqs.ecodelivery.dto.ReservationRequest;
import pt.ua.tqs.ecodelivery.dto.ReservationResponse;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;

/** REST boundary for reservations and usage sessions (epic: Reserva e Utilização). */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final SessionService sessionService;

    public ReservationController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** POST /api/reservations: reserve a bike for a period / temporal hold (US09) */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@RequestBody ReservationRequest request) {
        Reservation reservation = sessionService.reserve(
                request.courierId(),
                request.bikeId(),
                request.start(),
                request.end()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reservation));
    }

    /** POST /api/reservations/immediate: start using a bike now (US10, path B) */
    @PostMapping("/immediate")
    public ResponseEntity<ReservationResponse> startImmediateUse(@RequestBody ImmediateUseRequest request) {
        Reservation session = sessionService.startImmediateUse(request.courierId(), request.bikeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
    }

    /** POST /api/reservations/{id}/start?courierId= (US10, path A) */
    @PostMapping("/{id}/start")
    public ResponseEntity<ReservationResponse> startSession(@PathVariable Long id, @RequestParam Long courierId) {
        Reservation started = sessionService.startSession(id, courierId);
        return ResponseEntity.ok(toResponse(started));
    }

    /** POST /api/reservations/{id}/end?endHubId= */
    @PostMapping("/{id}/end")
    public ReservationResponse endSession(@PathVariable Long id, @RequestParam Long endHubId) {
        return toResponse(sessionService.endSession(id, endHubId));
    }

    /** POST /api/reservations/{id}/cancel */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable Long id) {
        Reservation cancelled = sessionService.cancel(id);
        return ResponseEntity.ok(toResponse(cancelled));
    }

    /** POST /api/reservations/expire: trigger hold expiration cleanup (US09) */
    @PostMapping("/expire")
    public ResponseEntity<List<ReservationResponse>> expireHolds() {
        List<ReservationResponse> list = sessionService.expireHolds().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    /** GET /api/reservations/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(sessionService.getReservation(id)));
    }

    /** GET /api/reservations?courierId=&status= (both optional) */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getCourierReservations(
            @RequestParam(required = false) Long courierId,
            @RequestParam(required = false) ReservationStatus status) {
        List<ReservationResponse> list = (status == null && courierId != null ? sessionService.findByCourier(courierId)
                        : sessionService.findReservations(courierId, status)).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getCourier() != null ? r.getCourier().getId() : null,
                r.getBike() != null ? r.getBike().getId() : null,
                r.getStartHub() != null ? r.getStartHub().getId() : null,
                r.getEndHub() != null ? r.getEndHub().getId() : null,
                r.getPlannedStart(),
                r.getPlannedEnd(),
                r.getActualStart(),
                r.getActualEnd(),
                r.getStatus()
        );
    }
}
