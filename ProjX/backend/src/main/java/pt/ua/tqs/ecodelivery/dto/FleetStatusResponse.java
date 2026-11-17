package pt.ua.tqs.ecodelivery.dto;

import java.util.Map;
import pt.ua.tqs.ecodelivery.model.BikeStatus;

/** Fleet overview for the operator dashboard. */
public record FleetStatusResponse(
        Map<BikeStatus,
        Long> bikesByStatus,
        long openTickets) {
}
