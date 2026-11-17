package pt.ua.tqs.ecodelivery.dto;

import pt.ua.tqs.ecodelivery.model.TicketPriority;

/** Body of a request to open a maintenance ticket. */
public record TicketRequest(
        Long bikeId,
        String description,
        TicketPriority priority) {
}
