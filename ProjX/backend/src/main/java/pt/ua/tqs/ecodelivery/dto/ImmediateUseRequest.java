package pt.ua.tqs.ecodelivery.dto;

/** Body of a request to start using a bike immediately. */
public record ImmediateUseRequest(
        Long courierId,
        Long bikeId) {
}
