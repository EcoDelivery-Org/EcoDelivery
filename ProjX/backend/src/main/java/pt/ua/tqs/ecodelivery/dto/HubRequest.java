package pt.ua.tqs.ecodelivery.dto;

/** Body of a request to create a hub. */
public record HubRequest(
        String name,
        String address,
        int capacity) {
}
