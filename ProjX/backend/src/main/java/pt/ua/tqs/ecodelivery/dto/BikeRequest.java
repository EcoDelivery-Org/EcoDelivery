package pt.ua.tqs.ecodelivery.dto;

/** Body of a request to register a bike. */
public record BikeRequest(
        String serialNumber,
        String model,
        double cargoCapacityKg,
        Long hubId) {
}
