package pt.ua.tqs.ecodelivery.maintenance;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.TicketRequest;
import pt.ua.tqs.ecodelivery.dto.TicketResponse;
import pt.ua.tqs.ecodelivery.model.MaintenanceTicket;
import pt.ua.tqs.ecodelivery.model.TicketStatus;

/** REST boundary for maintenance tickets (epic: Manutenção e Operações). */
@RestController
@RequestMapping("/api/maintenance/tickets")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /** POST /api/maintenance/tickets */
    @PostMapping
    public ResponseEntity<TicketResponse> openTicket(@Valid @RequestBody TicketRequest request) {
        MaintenanceTicket ticket = maintenanceService.openTicket(request.bikeId(), request.description(), request.priority());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(ticket));
    }

    /** GET /api/maintenance/tickets?status= */
    @GetMapping
    public List<TicketResponse> getTickets(@RequestParam(required = false) TicketStatus status) {
        return maintenanceService.findTickets(status).stream().map(MaintenanceController::toResponse).toList();
    }

    /** GET /api/maintenance/tickets/sla-breaches */
    @GetMapping("/sla-breaches")
    public List<TicketResponse> getSlaBreaches() {
        return maintenanceService.findSlaBreaches().stream().map(MaintenanceController::toResponse).toList();
    }

    /** GET /api/maintenance/tickets/{id} */
    @GetMapping("/{id}")
    public TicketResponse getTicket(@PathVariable Long id) {
        return toResponse(maintenanceService.getTicket(id));
    }

    /** PATCH /api/maintenance/tickets/{id}/assign?technician= */
    @PatchMapping("/{id}/assign")
    public TicketResponse assignTechnician(@PathVariable Long id, @RequestParam String technician) {
        return toResponse(maintenanceService.assignTechnician(id, technician));
    }

    /** POST /api/maintenance/tickets/{id}/close?resolution= */
    @PostMapping("/{id}/close")
    public TicketResponse closeTicket(@PathVariable Long id, @RequestParam String resolution) {
        return toResponse(maintenanceService.closeTicket(id, resolution));
    }

    public static TicketResponse toResponse(MaintenanceTicket t) {
        return new TicketResponse(t.getId(), t.getBike() != null ? t.getBike().getId() : null, t.getDescription(),
                t.getPriority(), t.getStatus(), t.getTechnician(), t.getOpenedAt(), t.getSlaDeadline(),
                t.getClosedAt(), t.getResolution());
    }
}
