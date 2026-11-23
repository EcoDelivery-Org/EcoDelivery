package pt.ua.tqs.ecodelivery.dto;

/** A hub as returned by the API. */
public record HubResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        int capacity) {
}
