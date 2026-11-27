package pt.ua.tqs.ecodelivery.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.CourierRequest;
import pt.ua.tqs.ecodelivery.dto.CourierResponse;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.service.CourierService;

/** REST boundary for couriers. */
@RestController
@RequestMapping("/api/couriers")
@CrossOrigin(origins = "*")
public class CourierController {

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PostMapping
    public ResponseEntity<CourierResponse> create(@Valid @RequestBody CourierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(courierService.create(request)));
    }

    /** GET /api/couriers?companyId= */
    @GetMapping
    public List<CourierResponse> list(@RequestParam(required = false) Long companyId) {
        return courierService.findAll(companyId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CourierResponse get(@PathVariable Long id) {
        return toResponse(courierService.get(id));
    }

    @PutMapping("/{id}")
    public CourierResponse update(@PathVariable Long id, @Valid @RequestBody CourierRequest request) {
        return toResponse(courierService.update(id, request));
    }

    private CourierResponse toResponse(Courier c) {
        return new CourierResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(),
                c.getCompany() != null ? c.getCompany().getId() : null, c.getCompany() != null);
    }
}
