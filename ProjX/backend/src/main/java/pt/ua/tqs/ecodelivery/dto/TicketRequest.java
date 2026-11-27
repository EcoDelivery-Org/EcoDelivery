package pt.ua.tqs.ecodelivery.dto;

import pt.ua.tqs.ecodelivery.model.DefectCategory;
import pt.ua.tqs.ecodelivery.model.TicketPriority;

/**
 * Body of a request to open a maintenance ticket (US03).
 *
 * @param targetResolutionHours optional SLA window; when absent the default for the priority is used
 * @param technician            optional technician to assign the ticket to right away
 */
public record TicketRequest(
        Long bikeId,
        String description,
        TicketPriority priority,
        DefectCategory defectCategory,
        Integer targetResolutionHours,
        String technician) {
}
