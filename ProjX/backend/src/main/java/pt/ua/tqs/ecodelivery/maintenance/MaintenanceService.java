package pt.ua.tqs.ecodelivery.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
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
    @Transactional
    public MaintenanceTicket openTicket(Long bikeId, String description, TicketPriority priority) {
        if (bikeId == null) {
            throw new IllegalArgumentException("Bike id must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Ticket description must not be empty");
        }
        Bike bike = bikeService.getBike(bikeId);
        bikeService.changeStatus(bikeId, BikeStatus.MAINTENANCE);

        TicketPriority effective = priority != null ? priority : TicketPriority.MEDIUM;
        LocalDateTime now = LocalDateTime.now(clock);
        MaintenanceTicket ticket = new MaintenanceTicket();
        ticket.setBike(bike);
        ticket.setDescription(description.trim());
        ticket.setPriority(effective);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setOpenedAt(now);
        ticket.setSlaDeadline(calculateSlaDeadline(effective, now));
        return ticketRepository.save(ticket);
    }

    @Transactional
    public MaintenanceTicket assignTechnician(Long ticketId, String technician) {
        if (technician == null || technician.isBlank()) {
            throw new IllegalArgumentException("Technician must not be empty");
        }
        MaintenanceTicket ticket = getTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleException("Ticket " + ticketId + " is already closed");
        }
        ticket.setTechnician(technician.trim());
        ticket.setStatus(TicketStatus.ASSIGNED);
        return ticketRepository.save(ticket);
    }

    /** Closes a ticket: the bike becomes AVAILABLE again (unless it has other open tickets or is retired). */
    @Transactional
    public MaintenanceTicket closeTicket(Long ticketId, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Resolution must not be empty");
        }
        MaintenanceTicket ticket = getTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleException("Ticket " + ticketId + " is already closed");
        }
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setResolution(resolution.trim());
        ticket.setClosedAt(LocalDateTime.now(clock));
        MaintenanceTicket saved = ticketRepository.save(ticket);

        Bike bike = saved.getBike();
        boolean otherOpen = ticketRepository.existsByBikeIdAndStatusNot(bike.getId(), TicketStatus.CLOSED);
        if (!otherOpen && bike.getStatus() == BikeStatus.MAINTENANCE) {
            bikeService.changeStatus(bike.getId(), BikeStatus.AVAILABLE);
        }
        return saved;
    }

    public MaintenanceTicket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
    }

    /** Lists tickets, optionally filtered by status. */
    public List<MaintenanceTicket> findTickets(TicketStatus status) {
        return status == null ? ticketRepository.findAll() : ticketRepository.findByStatus(status);
    }

    public List<MaintenanceTicket> findOpenTickets() {
        return ticketRepository.findByStatusNot(TicketStatus.CLOSED);
    }

    /** Open tickets whose SLA deadline has passed. */
    public List<MaintenanceTicket> findSlaBreaches() {
        LocalDateTime now = LocalDateTime.now(clock);
        return findOpenTickets().stream()
                .filter(t -> t.getSlaDeadline() != null && t.getSlaDeadline().isBefore(now))
                .toList();
    }

    private LocalDateTime calculateSlaDeadline(TicketPriority priority, LocalDateTime openedAt) {
        return openedAt.plus(switch (priority) {
            case HIGH -> Duration.ofHours(4);
            case MEDIUM -> Duration.ofHours(24);
            case LOW -> Duration.ofHours(72);
        });
    }
}
