package pt.ua.tqs.ecodelivery.service;

import java.util.List;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.port.GeocodingPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

/** Business logic for hubs, including finding nearby hubs by address. */
@Service
public class HubService {

    private final HubRepository hubRepository;
    private final BikeRepository bikeRepository;
    private final GeocodingPort geocodingPort;

    public HubService(HubRepository hubRepository, BikeRepository bikeRepository, GeocodingPort geocodingPort) {
        this.hubRepository = hubRepository;
        this.bikeRepository = bikeRepository;
        this.geocodingPort = geocodingPort;
    }

    /** Creates a hub; its address is geocoded into coordinates. */
    public Hub createHub(HubRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<Hub> findAll() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Hub getHub(Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Bikes at the hub that can be reserved now. */
    public List<Bike> getAvailableBikes(Long hubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Hubs within a radius of an address (Hub Finder). */
    public List<Hub> findNearbyHubs(String address, double radiusKm) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Whether the hub can receive one more bike. */
    public boolean hasCapacity(Long hubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
