package pt.ua.tqs.ecodelivery.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.BikeResponse;
import pt.ua.tqs.ecodelivery.dto.FleetStatusResponse;
import pt.ua.tqs.ecodelivery.dto.TicketResponse;
import pt.ua.tqs.ecodelivery.maintenance.MaintenanceService;
import pt.ua.tqs.ecodelivery.service.BikeService;
import pt.ua.tqs.ecodelivery.service.HubService;

/** REST boundary for the Operator Dashboard: fleet overview and bike activation (Reserved for US02 & US06). */
@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private final BikeService bikeService;
    private final HubService hubService;
    private final MaintenanceService maintenanceService;

    public OperatorController(BikeService bikeService, HubService hubService, MaintenanceService maintenanceService) {
        this.bikeService = bikeService;
        this.hubService = hubService;
        this.maintenanceService = maintenanceService;
    }

    /** GET /api/operator/fleet-status (Reserved for US06) */
    @GetMapping("/fleet-status")
    public FleetStatusResponse getFleetStatus() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/operator/tickets/open (Reserved for US03 / US06) */
    @GetMapping("/tickets/open")
    public List<TicketResponse> getOpenTickets() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** POST /api/operator/bikes/{id}/activate (US02) */
    @PostMapping("/bikes/{id}/activate")
    public BikeResponse activateBike(@PathVariable Long id) {
        return toResponse(bikeService.activate(id));
    }

    /** POST /api/operator/bikes/{id}/deactivate (US02) */
    @PostMapping("/bikes/{id}/deactivate")
    public BikeResponse deactivateBike(@PathVariable Long id) {
        return toResponse(bikeService.deactivate(id));
    }

    private BikeResponse toResponse(pt.ua.tqs.ecodelivery.model.Bike bike) {
        return new BikeResponse(bike.getId(), bike.getSerialNumber(), bike.getModel(),
                bike.getCargoCapacityKg(), bike.getBatteryLevel(), bike.getStatus(),
                bike.isActive(), bike.getCurrentHub() == null ? null : bike.getCurrentHub().getId());
    }
}
