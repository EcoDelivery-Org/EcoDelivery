package pt.ua.tqs.ecodelivery.maintenance;

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
import pt.ua.tqs.ecodelivery.dto.TicketRequest;
import pt.ua.tqs.ecodelivery.dto.TicketResponse;
import pt.ua.tqs.ecodelivery.model.MaintenanceTicket;
import pt.ua.tqs.ecodelivery.model.TicketStatus;

/** REST boundary for maintenance tickets (epic: Manutenção e Operações). */
@RestController
@RequestMapping("/api/maintenance/tickets")
@CrossOrigin(origins = "*")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /** POST /api/maintenance/tickets - open a work order (US03) */
    @PostMapping
    public ResponseEntity<TicketResponse> openTicket(@RequestBody TicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(maintenanceService.openTicket(request)));
    }

    /** GET /api/maintenance/tickets?status=&technician= - the technician dashboard feed when filtered (US03) */
    @GetMapping
    public List<TicketResponse> getTickets(@RequestParam(required = false) TicketStatus status,
                                           @RequestParam(required = false) String technician) {
        return maintenanceService.findTickets(status, technician).stream()
                .map(MaintenanceController::toResponse).toList();
    }

    /** PATCH /api/maintenance/tickets/{id}/assign?technician= (US03) */
    @PatchMapping("/{id}/assign")
    public TicketResponse assignTechnician(@PathVariable Long id, @RequestParam String technician) {
        return toResponse(maintenanceService.assignTechnician(id, technician));
    }

    /** POST /api/maintenance/tickets/{id}/close?resolution= (US04, not implemented yet) */
    @PostMapping("/{id}/close")
    public TicketResponse closeTicket(@PathVariable Long id, @RequestParam String resolution) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Maps a ticket to its API representation; also used by the operator controller. */
    public static TicketResponse toResponse(MaintenanceTicket t) {
        return new TicketResponse(t.getId(), t.getBike() != null ? t.getBike().getId() : null,
                t.getDescription(), t.getDefectCategory(), t.getPriority(), t.getStatus(), t.getTechnician(),
                t.getOpenedAt(), t.getSlaDeadline(), t.getClosedAt(), t.getResolution());
    }
}
