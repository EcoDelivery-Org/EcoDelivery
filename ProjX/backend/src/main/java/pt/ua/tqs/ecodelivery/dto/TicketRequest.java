package pt.ua.tqs.ecodelivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pt.ua.tqs.ecodelivery.model.TicketPriority;

/** Body of a request to open a maintenance ticket. */
public record TicketRequest(
        @NotNull(message = "Bike id is required")
        Long bikeId,

        @NotBlank(message = "Description is required")
        String description,

        TicketPriority priority) {
}
