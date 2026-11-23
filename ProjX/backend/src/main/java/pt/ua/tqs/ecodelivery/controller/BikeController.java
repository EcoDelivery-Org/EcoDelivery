package pt.ua.tqs.ecodelivery.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.BikeRequest;
import pt.ua.tqs.ecodelivery.dto.BikeResponse;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.service.BikeService;

/** REST boundary for the cargo e-bikes of the fleet (epic: Gestão de Frota e Hubs). */
@RestController
@RequestMapping("/api/bikes")
public class BikeController {

    private final BikeService bikeService;

    public BikeController(BikeService bikeService) {
        this.bikeService = bikeService;
    }

    /** POST /api/bikes */
    @PostMapping
    public ResponseEntity<BikeResponse> createBike(@RequestBody BikeRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/bikes?hubId=&status= */
    @GetMapping
    public List<BikeResponse> getBikes(@RequestParam(required = false) Long hubId,
                                       @RequestParam(required = false) BikeStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/bikes/{id} */
    @GetMapping("/{id}")
    public BikeResponse getBike(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** PATCH /api/bikes/{id}/status?status= */
    @PatchMapping("/{id}/status")
    public BikeResponse updateStatus(@PathVariable Long id, @RequestParam BikeStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** PATCH /api/bikes/{id}/hub?hubId= */
    @PatchMapping("/{id}/hub")
    public BikeResponse moveToHub(@PathVariable Long id, @RequestParam Long hubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
