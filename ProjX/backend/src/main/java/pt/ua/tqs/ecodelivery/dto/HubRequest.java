package pt.ua.tqs.ecodelivery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Body of a request to create or configure a hub. */
public record HubRequest(
        @NotBlank(message = "Hub name is required")
        String name,

        @NotBlank(message = "Hub address is required")
        String address,

        @Min(value = 1, message = "Hub capacity must be at least 1")
        int capacity,

        Double latitude,

        Double longitude,

        Double boundaryRadiusMeters) {

    public HubRequest(String name, String address, int capacity) {
        this(name, address, capacity, null, null, 50.0);
    }

    public HubRequest(String name, String address, int capacity, Double latitude, Double longitude) {
        this(name, address, capacity, latitude, longitude, 50.0);
    }
}
