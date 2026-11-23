package pt.ua.tqs.ecodelivery.adapter;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pt.ua.tqs.ecodelivery.model.Coordinates;
import pt.ua.tqs.ecodelivery.port.GeocodingPort;

/** Adapter for the OpenStreetMap Nominatim geocoding API. */
@Component
public class NominatimGeocodingAdapter implements GeocodingPort {

    private final RestClient restClient;
    private final String baseUrl;

    public NominatimGeocodingAdapter(@Value("${ecodelivery.geocoding.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Optional<Coordinates> geocode(String address) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<String> reverseGeocode(Coordinates coordinates) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
