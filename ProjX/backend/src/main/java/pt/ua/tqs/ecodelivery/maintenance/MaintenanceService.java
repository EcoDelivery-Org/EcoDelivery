package pt.ua.tqs.ecodelivery.maintenance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.model.MaintenanceTicket;
import pt.ua.tqs.ecodelivery.model.TicketPriority;
import pt.ua.tqs.ecodelivery.model.TicketStatus;
import pt.ua.tqs.ecodelivery.repository.MaintenanceTicketRepository;
import pt.ua.tqs.ecodelivery.service.BikeService;

/** Business logic for maintenance tickets and their SLA deadlines. */
@Service
public class MaintenanceService {

    private final MaintenanceTicketRepository ticketRepository;
    private final BikeService bikeService;
    private final Clock clock;

    public MaintenanceService(MaintenanceTicketRepository ticketRepository, BikeService bikeService, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.bikeService = bikeService;
        this.clock = clock;
    }

    /** Opens a ticket for a bike: the bike goes to MAINTENANCE and the SLA deadline is set. */
    public MaintenanceTicket openTicket(Long bikeId, String description, TicketPriority priority) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public MaintenanceTicket assignTechnician(Long ticketId, String technician) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Closes a ticket: the bike becomes AVAILABLE again. */
    public MaintenanceTicket closeTicket(Long ticketId, String resolution) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Lists tickets, optionally filtered by status. */
    public List<MaintenanceTicket> findTickets(TicketStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<MaintenanceTicket> findOpenTickets() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Open tickets whose SLA deadline has passed. */
    public List<MaintenanceTicket> findSlaBreaches() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private LocalDateTime calculateSlaDeadline(TicketPriority priority, LocalDateTime openedAt) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
