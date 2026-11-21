package pt.ua.tqs.ecodelivery.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pt.ua.tqs.ecodelivery.model.Coordinates;
import pt.ua.tqs.ecodelivery.port.GeocodingPort;

/** Adapter for the OpenStreetMap Nominatim geocoding API. */
@Component
public class NominatimGeocodingAdapter implements GeocodingPort {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingAdapter.class);

    private final RestClient restClient;
    private final String baseUrl;

    public NominatimGeocodingAdapter(@Value("${ecodelivery.geocoding.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, "EcoDelivery-TQS/1.0")
                .build();
    }

    @Override
    public Optional<Coordinates> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response != null && !response.isEmpty()) {
                Map<String, Object> first = response.getFirst();
                double lat = Double.parseDouble(first.get("lat").toString());
                double lon = Double.parseDouble(first.get("lon").toString());
                return Optional.of(new Coordinates(lat, lon));
            }
        } catch (Exception e) {
            log.warn("Geocoding failed for address '{}': {}", address, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> reverseGeocode(Coordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", coordinates.latitude())
                            .queryParam("lon", coordinates.longitude())
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("display_name")) {
                return Optional.ofNullable((String) response.get("display_name"));
            }
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for coordinates {}: {}", coordinates, e.getMessage());
        }
        return Optional.empty();
    }
}
