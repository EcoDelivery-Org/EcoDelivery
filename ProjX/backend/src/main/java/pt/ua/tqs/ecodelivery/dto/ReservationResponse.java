package pt.ua.tqs.ecodelivery.dto;

import java.time.LocalDateTime;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;

/** A reservation / usage session as returned by the API. */
public record ReservationResponse(
        Long id,
        Long courierId,
        Long bikeId,
        Long startHubId,
        Long endHubId,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        ReservationStatus status) {
}
