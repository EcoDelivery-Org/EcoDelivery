package pt.ua.tqs.ecodelivery.dto;

import java.time.LocalDateTime;

/** Body of a request to reserve a bike for a period. */
public record ReservationRequest(
        Long courierId,
        Long bikeId,
        LocalDateTime start,
        LocalDateTime end) {
}
