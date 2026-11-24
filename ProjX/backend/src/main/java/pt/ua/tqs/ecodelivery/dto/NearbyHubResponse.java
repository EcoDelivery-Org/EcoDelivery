package pt.ua.tqs.ecodelivery.dto;

/** A hub returned by the Hub Finder (US08), with its distance to the searched address and its bookable bikes. */
public record NearbyHubResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        double distanceKm,
        int availableBikes) {
}
