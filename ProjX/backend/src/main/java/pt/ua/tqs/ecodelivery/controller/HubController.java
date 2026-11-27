package pt.ua.tqs.ecodelivery.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.BikeResponse;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.dto.HubResponse;
import pt.ua.tqs.ecodelivery.dto.NearbyHubResponse;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.service.HubService;
import pt.ua.tqs.ecodelivery.service.NearbyHub;

/** REST boundary for hubs: inventory management (US01) and hub search and availability discovery (US08). */
@RestController
@RequestMapping("/api/hubs")
@CrossOrigin(origins = "*")
public class HubController {

    private final HubService hubService;

    public HubController(HubService hubService) {
        this.hubService = hubService;
    }

    /** POST /api/hubs - Provision new hub (US01) */
    @PostMapping
    public ResponseEntity<HubResponse> createHub(@Valid @RequestBody HubRequest request) {
        Hub hub = hubService.createHub(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(hub));
    }

    /** PUT /api/hubs/{id} - Update hub details */
    @PutMapping("/{id}")
    public HubResponse updateHub(@PathVariable Long id, @Valid @RequestBody HubRequest request) {
        return toResponse(hubService.updateHub(id, request));
    }

    /** DELETE /api/hubs/{id} - Remove an empty hub */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHub(@PathVariable Long id) {
        hubService.deleteHub(id);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/hubs - List all provisioned hubs (US01) */
    @GetMapping
    public List<HubResponse> getHubs() {
        return hubService.findAll().stream().map(this::toResponse).toList();
    }

    /** GET /api/hubs/{id} - Get hub by id (US01) */
    @GetMapping("/{id}")
    public HubResponse getHub(@PathVariable Long id) {
        return toResponse(hubService.getHub(id));
    }

    /** GET /api/hubs/{id}/bikes - Bikes that can be booked now at the hub, highest battery first (US08) */
    @GetMapping("/{id}/bikes")
    public List<BikeResponse> getAvailableBikes(@PathVariable Long id) {
        return hubService.getAvailableBikes(id).stream().map(this::toBikeResponse).toList();
    }

    /** GET /api/hubs/nearby?address=&radiusKm= - Hub Finder: hubs within a radius, nearest first (US08) */
    @GetMapping("/nearby")
    public List<NearbyHubResponse> findNearbyHubs(@RequestParam String address, @RequestParam double radiusKm) {
        return hubService.findNearbyHubs(address, radiusKm).stream().map(this::toNearbyResponse).toList();
    }

    private NearbyHubResponse toNearbyResponse(NearbyHub nearby) {
        Hub hub = nearby.hub();
        return new NearbyHubResponse(
                hub.getId(),
                hub.getName(),
                hub.getAddress(),
                hub.getLatitude(),
                hub.getLongitude(),
                Math.round(nearby.distanceKm() * 100.0) / 100.0,
                nearby.availableBikes()
        );
    }

    private BikeResponse toBikeResponse(Bike bike) {
        return new BikeResponse(
                bike.getId(),
                bike.getSerialNumber(),
                bike.getModel(),
                bike.getCargoCapacityKg(),
                bike.getBatteryLevel(),
                bike.getStatus(),
                bike.isActive(),
                bike.getCurrentHub() != null ? bike.getCurrentHub().getId() : null
        );
    }

    private HubResponse toResponse(Hub hub) {
        int currentBikes = (int) hubService.getBikeCount(hub.getId());
        int availableCapacity = Math.max(0, hub.getCapacity() - currentBikes);
        return new HubResponse(
                hub.getId(),
                hub.getName(),
                hub.getAddress(),
                hub.getLatitude(),
                hub.getLongitude(),
                hub.getCapacity(),
                hub.getBoundaryRadiusMeters(),
                currentBikes,
                availableCapacity
        );
    }
}
