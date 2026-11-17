package pt.ua.tqs.ecodelivery.maintenance;

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
import pt.ua.tqs.ecodelivery.dto.TicketRequest;
import pt.ua.tqs.ecodelivery.dto.TicketResponse;
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
    public ResponseEntity<TicketResponse> openTicket(@RequestBody TicketRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/maintenance/tickets?status= */
    @GetMapping
    public List<TicketResponse> getTickets(@RequestParam(required = false) TicketStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** PATCH /api/maintenance/tickets/{id}/assign?technician= */
    @PatchMapping("/{id}/assign")
    public TicketResponse assignTechnician(@PathVariable Long id, @RequestParam String technician) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** POST /api/maintenance/tickets/{id}/close?resolution= */
    @PostMapping("/{id}/close")
    public TicketResponse closeTicket(@PathVariable Long id, @RequestParam String resolution) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
