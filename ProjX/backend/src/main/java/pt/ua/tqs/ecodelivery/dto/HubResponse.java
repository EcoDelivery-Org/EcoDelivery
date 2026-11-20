package pt.ua.tqs.ecodelivery.dto;

/** A hub as returned by the API. */
public record HubResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        int capacity,
        double boundaryRadiusMeters,
        int currentBikes,
        int availableCapacity) {

    public HubResponse(Long id, String name, String address, double latitude, double longitude, int capacity) {
        this(id, name, address, latitude, longitude, capacity, 50.0, 0, capacity);
    }

    public HubResponse(Long id, String name, String address, double latitude, double longitude, int capacity, double boundaryRadiusMeters) {
        this(id, name, address, latitude, longitude, capacity, boundaryRadiusMeters, 0, capacity);
    }
}
