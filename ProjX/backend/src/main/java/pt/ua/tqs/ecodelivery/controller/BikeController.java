package pt.ua.tqs.ecodelivery.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.service.BikeService;

/** REST boundary for vehicle inventory management (US01). */
@RestController
@RequestMapping("/api/bikes")
@CrossOrigin(origins = "*")
public class BikeController {

    private final BikeService bikeService;

    public BikeController(BikeService bikeService) {
        this.bikeService = bikeService;
    }

    /** POST /api/bikes - Register new e-bike asset (US01) */
    @PostMapping
    public ResponseEntity<BikeResponse> createBike(@Valid @RequestBody BikeRequest request) {
        Bike bike = bikeService.registerBike(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(bike));
    }

    /** GET /api/bikes?hubId=&status= - Query bike inventory (US01) */
    @GetMapping
    public List<BikeResponse> getBikes(@RequestParam(required = false) Long hubId,
                                       @RequestParam(required = false) BikeStatus status) {
        return bikeService.findBikes(hubId, status).stream().map(this::toResponse).toList();
    }

    /** GET /api/bikes/{id} - Query bike asset details (US01) */
    @GetMapping("/{id}")
    public BikeResponse getBike(@PathVariable Long id) {
        return toResponse(bikeService.getBike(id));
    }

    /** GET /api/bikes/{id}/available: whether bike can be reserved now (US09) */
    @GetMapping("/{id}/available")
    public boolean isAvailableForReservation(@PathVariable Long id) {
        return bikeService.isAvailableForReservation(id);
    }

    /** PATCH /api/bikes/{id}/status?status= (Reserved for US02) */
    @PatchMapping("/{id}/status")
    public BikeResponse updateStatus(@PathVariable Long id, @RequestParam BikeStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** PATCH /api/bikes/{id}/hub?hubId= (Reserved for future relocation stories) */
    @PatchMapping("/{id}/hub")
    public BikeResponse moveToHub(@PathVariable Long id, @RequestParam Long hubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private BikeResponse toResponse(Bike bike) {
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
}
