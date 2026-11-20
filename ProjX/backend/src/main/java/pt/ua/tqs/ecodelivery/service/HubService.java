package pt.ua.tqs.ecodelivery.service;

import java.util.List;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.port.GeocodingPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

/** Business logic for hubs and hub inventory management (US01). */
@Service
public class HubService {

    private final HubRepository hubRepository;
    private final BikeRepository bikeRepository;
    private final GeocodingPort geocodingPort;
    private final InventoryLogService inventoryLogService;

    public HubService(HubRepository hubRepository, BikeRepository bikeRepository,
                      GeocodingPort geocodingPort, InventoryLogService inventoryLogService) {
        this.hubRepository = hubRepository;
        this.bikeRepository = bikeRepository;
        this.geocodingPort = geocodingPort;
        this.inventoryLogService = inventoryLogService;
    }

    /** Creates a hub; its address is geocoded into coordinates if not provided (US01). */
    public Hub createHub(HubRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessRuleException("Hub name cannot be empty");
        }
        if (request.capacity() <= 0) {
            throw new BusinessRuleException("Hub capacity must be greater than zero");
        }

        double latitude = 0.0;
        double longitude = 0.0;

        if (request.latitude() != null && request.longitude() != null &&
                (request.latitude() != 0.0 || request.longitude() != 0.0)) {
            latitude = request.latitude();
            longitude = request.longitude();
        } else if (request.address() != null && !request.address().isBlank()) {
            var coordsOpt = geocodingPort.geocode(request.address());
            if (coordsOpt.isPresent()) {
                latitude = coordsOpt.get().latitude();
                longitude = coordsOpt.get().longitude();
            } else {
                throw new BusinessRuleException("Unable to geocode address: " + request.address() + ". Please provide coordinates manually.");
            }
        } else {
            throw new BusinessRuleException("Hub must have an address or coordinates defined");
        }

        double radius = (request.boundaryRadiusMeters() != null && request.boundaryRadiusMeters() > 0)
                ? request.boundaryRadiusMeters()
                : 50.0;

        Hub hub = new Hub();
        hub.setName(request.name().trim());
        hub.setAddress(request.address() != null ? request.address().trim() : "");
        hub.setLatitude(latitude);
        hub.setLongitude(longitude);
        hub.setCapacity(request.capacity());
        hub.setBoundaryRadiusMeters(radius);

        Hub saved = hubRepository.save(hub);
        inventoryLogService.logHubProvisioned(saved);
        return saved;
    }

    public List<Hub> findAll() {
        return hubRepository.findAll();
    }

    public Hub getHub(Long id) {
        return hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + id));
    }

    /** Whether the hub can receive one more bike (US01 capacity check). */
    public boolean hasCapacity(Long hubId) {
        Hub hub = getHub(hubId);
        long currentBikes = bikeRepository.countByCurrentHubId(hubId);
        return currentBikes < hub.getCapacity();
    }

    public long getBikeCount(Long hubId) {
        return bikeRepository.countByCurrentHubId(hubId);
    }

    /** Bikes at the hub that can be reserved now (Reserved for US08). */
    public List<Bike> getAvailableBikes(Long hubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Hubs within a radius of an address (Hub Finder) (Reserved for US08). */
    public List<Hub> findNearbyHubs(String address, double radiusKm) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
