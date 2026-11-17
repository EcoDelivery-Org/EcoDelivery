package pt.ua.tqs.ecodelivery.dto;

import pt.ua.tqs.ecodelivery.model.BikeStatus;

/** A bike as returned by the API. */
public record BikeResponse(
        Long id,
        String serialNumber,
        String model,
        double cargoCapacityKg,
        int batteryLevel,
        BikeStatus status,
        boolean active,
        Long hubId) {
}
