package pt.ua.tqs.ecodelivery.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
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
import pt.ua.tqs.ecodelivery.service.HubService;

/** REST boundary for hubs, including the Hub Finder (epic: Gestão de Frota e Hubs). */
@RestController
@RequestMapping("/api/hubs")
public class HubController {

    private final HubService hubService;

    public HubController(HubService hubService) {
        this.hubService = hubService;
    }

    /** POST /api/hubs */
    @PostMapping
    public ResponseEntity<HubResponse> createHub(@RequestBody HubRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/hubs */
    @GetMapping
    public List<HubResponse> getHubs() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/hubs/{id} */
    @GetMapping("/{id}")
    public HubResponse getHub(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/hubs/{id}/bikes: bikes available at the hub */
    @GetMapping("/{id}/bikes")
    public List<BikeResponse> getAvailableBikes(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/hubs/nearby?address=&radiusKm= (Hub Finder) */
    @GetMapping("/nearby")
    public List<HubResponse> findNearbyHubs(@RequestParam String address, @RequestParam double radiusKm) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
