package pt.ua.tqs.ecodelivery.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Coordinates;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.port.GeocodingPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

/** Business logic for hubs: inventory management (US01) and hub search and availability discovery (US08). */
@Service
public class HubService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final HubRepository hubRepository;
    private final BikeRepository bikeRepository;
    private final GeocodingPort geocodingPort;
    private final InventoryLogService inventoryLogService;
    private final PolicyService policyService;

    public HubService(HubRepository hubRepository, BikeRepository bikeRepository,
                      GeocodingPort geocodingPort, InventoryLogService inventoryLogService,
                      PolicyService policyService) {
        this.hubRepository = hubRepository;
        this.bikeRepository = bikeRepository;
        this.geocodingPort = geocodingPort;
        this.inventoryLogService = inventoryLogService;
        this.policyService = policyService;
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

    /** Updates a hub's name, address, capacity and geofence radius; coordinates are re-geocoded if the address changes. */
    @Transactional
    public Hub updateHub(Long id, HubRequest request) {
        Hub hub = getHub(id);
        if (request.capacity() <= 0) {
            throw new BusinessRuleException("Hub capacity must be greater than zero");
        }
        long parked = bikeRepository.countByCurrentHubId(id);
        if (request.capacity() < parked) {
            throw new BusinessRuleException("Hub has " + parked + " bikes parked; capacity cannot be below that");
        }
        if (request.name() != null && !request.name().isBlank()) {
            hub.setName(request.name().trim());
        }
        if (request.latitude() != null && request.longitude() != null) {
            hub.setLatitude(request.latitude());
            hub.setLongitude(request.longitude());
            hub.setAddress(request.address() != null ? request.address().trim() : hub.getAddress());
        } else if (request.address() != null && !request.address().isBlank()
                && !request.address().trim().equals(hub.getAddress())) {
            var coords = geocodingPort.geocode(request.address().trim())
                    .orElseThrow(() -> new BusinessRuleException("Unable to geocode address: " + request.address()));
            hub.setAddress(request.address().trim());
            hub.setLatitude(coords.latitude());
            hub.setLongitude(coords.longitude());
        }
        hub.setCapacity(request.capacity());
        if (request.boundaryRadiusMeters() != null && request.boundaryRadiusMeters() > 0) {
            hub.setBoundaryRadiusMeters(request.boundaryRadiusMeters());
        }
        return hubRepository.save(hub);
    }

    /** Deletes a hub; refused while bikes are parked there. */
    @Transactional
    public void deleteHub(Long id) {
        Hub hub = getHub(id);
        if (bikeRepository.countByCurrentHubId(id) > 0) {
            throw new BusinessRuleException("Hub '" + hub.getName() + "' still has bikes parked and cannot be deleted");
        }
        hubRepository.delete(hub);
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

    /**
     * Bikes at the hub that can be booked now (US08): available, active (BR2) and with the battery
     * at or above the minimum level configured in the usage policy (BR1). Sorted by battery, highest first,
     * so the courier can compare battery levels.
     */
    public List<Bike> getAvailableBikes(Long hubId) {
        getHub(hubId);
        return bookableBikesAt(hubId);
    }

    /**
     * Hubs within a radius of an address (Hub Finder, US08), nearest first. The address is geocoded
     * through the GeocodingPort; each hub carries its distance and its number of bookable bikes.
     */
    public List<NearbyHub> findNearbyHubs(String address, double radiusKm) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Search address cannot be empty");
        }
        if (!(radiusKm > 0)) {
            throw new IllegalArgumentException("Search radius must be greater than zero");
        }
        Coordinates origin = geocodingPort.geocode(address.trim())
                .orElseThrow(() -> new BusinessRuleException("Unable to geocode address: " + address.trim()));

        List<NearbyHub> result = new ArrayList<>();
        for (Hub hub : hubRepository.findAll()) {
            double distance = distanceKm(origin, hub);
            if (distance <= radiusKm) {
                result.add(new NearbyHub(hub, distance, bookableBikesAt(hub.getId()).size()));
            }
        }
        result.sort(Comparator.comparingDouble(NearbyHub::distanceKm));
        return result;
    }

    private List<Bike> bookableBikesAt(Long hubId) {
        int minBatteryLevel = policyService.getMinBatteryLevel();
        return bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(hubId, BikeStatus.AVAILABLE).stream()
                .filter(bike -> bike.getBatteryLevel() >= minBatteryLevel)
                .sorted(Comparator.comparingInt(Bike::getBatteryLevel).reversed())
                .toList();
    }

    /** Great-circle (haversine) distance, in km, between a point and a hub. */
    private static double distanceKm(Coordinates origin, Hub hub) {
        double dLat = Math.toRadians(hub.getLatitude() - origin.latitude());
        double dLon = Math.toRadians(hub.getLongitude() - origin.longitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(origin.latitude())) * Math.cos(Math.toRadians(hub.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
