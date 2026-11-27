package pt.ua.tqs.ecodelivery.dto;

/** Body of a request to update a bike's descriptive attributes; null fields are left unchanged. */
public record BikeUpdateRequest(String model, Double cargoCapacityKg, Integer batteryLevel) {
}
