package pt.ua.tqs.ecodelivery.reservation;

import java.util.List;
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

/** REST boundary for reservations and usage sessions (epic: Reserva e Utilização). */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final SessionService sessionService;

    public ReservationController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** POST /api/reservations: reserve a bike for a period */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@RequestBody ReservationRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** POST /api/reservations/immediate: start using a bike now */
    @PostMapping("/immediate")
    public ResponseEntity<ReservationResponse> startImmediateUse(@RequestBody ImmediateUseRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** POST /api/reservations/{id}/start */
    @PostMapping("/{id}/start")
    public ReservationResponse startSession(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** POST /api/reservations/{id}/end?endHubId= */
    @PostMapping("/{id}/end")
    public ReservationResponse endSession(@PathVariable Long id, @RequestParam Long endHubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** POST /api/reservations/{id}/cancel */
    @PostMapping("/{id}/cancel")
    public ReservationResponse cancelReservation(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/reservations/{id} */
    @GetMapping("/{id}")
    public ReservationResponse getReservation(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/reservations?courierId= */
    @GetMapping
    public List<ReservationResponse> getCourierReservations(@RequestParam Long courierId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
