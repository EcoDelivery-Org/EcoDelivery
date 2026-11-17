package pt.ua.tqs.ecodelivery.port;

import java.util.Optional;
import pt.ua.tqs.ecodelivery.model.Coordinates;

/** Port to a geocoding service: converts addresses to coordinates and back. */
public interface GeocodingPort {

    Optional<Coordinates> geocode(String address);

    Optional<String> reverseGeocode(Coordinates coordinates);
}
