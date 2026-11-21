package pt.ua.tqs.ecodelivery.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.BikeResponse;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.dto.HubResponse;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.service.HubService;

/** REST boundary for hubs and hub inventory management (US01). */
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

    /** GET /api/hubs/{id}/bikes: bikes available at the hub (Reserved for US08) */
    @GetMapping("/{id}/bikes")
    public List<BikeResponse> getAvailableBikes(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/hubs/nearby?address=&radiusKm= (Hub Finder) (Reserved for US08) */
    @GetMapping("/nearby")
    public List<HubResponse> findNearbyHubs(@RequestParam String address, @RequestParam double radiusKm) {
        throw new UnsupportedOperationException("Not implemented yet");
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
