package pt.ua.tqs.ecodelivery.dto;

import java.time.LocalDateTime;
import pt.ua.tqs.ecodelivery.model.TicketPriority;
import pt.ua.tqs.ecodelivery.model.TicketStatus;

/** A maintenance ticket as returned by the API. */
public record TicketResponse(
        Long id,
        Long bikeId,
        String description,
        TicketPriority priority,
        TicketStatus status,
        String technician,
        LocalDateTime openedAt,
        LocalDateTime slaDeadline,
        LocalDateTime closedAt,
        String resolution) {
}
