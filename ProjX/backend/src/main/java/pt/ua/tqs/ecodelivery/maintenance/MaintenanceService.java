package pt.ua.tqs.ecodelivery.maintenance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.dto.TicketRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.DefectCategory;
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

    /**
     * US03: opens a work order for a bike. The bike goes to MAINTENANCE (so it leaves courier
     * discovery and its pending reservations are cancelled) and the SLA deadline is set from the
     * requested target window or, if none, from the priority. A technician can be assigned right away.
     */
    @Transactional
    public MaintenanceTicket openTicket(TicketRequest request) {
        if (request == null || request.bikeId() == null) {
            throw new IllegalArgumentException("Bike id must not be null");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new IllegalArgumentException("Ticket description must not be empty");
        }
        if (request.priority() == null) {
            throw new IllegalArgumentException("Ticket priority must not be null");
        }
        if (request.targetResolutionHours() != null && request.targetResolutionHours() <= 0) {
            throw new IllegalArgumentException("Target resolution time must be greater than zero hours");
        }
        DefectCategory category = request.defectCategory() != null ? request.defectCategory() : DefectCategory.OTHER;

        // Throws ResourceNotFoundException when the bike does not exist
        Bike bike = bikeService.getBike(request.bikeId());
        if (bike.getStatus() == BikeStatus.RETIRED) {
            throw new BusinessRuleException("Bike " + bike.getId() + " is retired and cannot be sent to maintenance");
        }
        bikeService.changeStatus(bike.getId(), BikeStatus.MAINTENANCE);

        LocalDateTime now = LocalDateTime.now(clock);
        MaintenanceTicket ticket = new MaintenanceTicket();
        ticket.setBike(bike);
        ticket.setDescription(request.description().trim());
        ticket.setDefectCategory(category);
        ticket.setPriority(request.priority());
        ticket.setOpenedAt(now);
        ticket.setSlaDeadline(request.targetResolutionHours() != null
                ? now.plusHours(request.targetResolutionHours())
                : calculateSlaDeadline(request.priority(), now));
        ticket.setStatus(TicketStatus.OPEN);

        String technician = request.technician();
        if (technician != null && !technician.isBlank()) {
            ticket.setTechnician(technician.trim());
            ticket.setStatus(TicketStatus.ASSIGNED);
        }
        return ticketRepository.save(ticket);
    }

    /** US03: assigns (or reassigns) an unfinished ticket to a technician; it then shows on their dashboard. */
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

    /** Closes a ticket: the bike becomes AVAILABLE again (US04, not implemented yet). */
    public MaintenanceTicket closeTicket(Long ticketId, String resolution) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public MaintenanceTicket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
    }

    /**
     * Lists tickets, optionally filtered by status and/or technician. With a technician this is the
     * technician's dashboard feed, ordered by SLA deadline (most urgent first).
     */
    public List<MaintenanceTicket> findTickets(TicketStatus status, String technician) {
        boolean hasTechnician = technician != null && !technician.isBlank();
        if (hasTechnician && status != null) {
            return ticketRepository.findByTechnicianAndStatusOrderBySlaDeadlineAsc(technician.trim(), status);
        }
        if (hasTechnician) {
            return ticketRepository.findByTechnicianOrderBySlaDeadlineAsc(technician.trim());
        }
        if (status != null) {
            return ticketRepository.findByStatusOrderByOpenedAtDesc(status);
        }
        return ticketRepository.findAllByOrderByOpenedAtDesc();
    }

    /** Tickets that are not closed yet, most urgent SLA first. */
    public List<MaintenanceTicket> findOpenTickets() {
        return ticketRepository.findByStatusNotOrderBySlaDeadlineAsc(TicketStatus.CLOSED);
    }

    /** Open tickets whose SLA deadline has passed. */
    public List<MaintenanceTicket> findSlaBreaches() {
        LocalDateTime now = LocalDateTime.now(clock);
        return findOpenTickets().stream().filter(t -> t.getSlaDeadline().isBefore(now)).toList();
    }

    /** Default SLA window per priority: HIGH 4h, MEDIUM 24h, LOW 72h. */
    LocalDateTime calculateSlaDeadline(TicketPriority priority, LocalDateTime openedAt) {
        return switch (priority) {
            case HIGH -> openedAt.plusHours(4);
            case MEDIUM -> openedAt.plusHours(24);
            case LOW -> openedAt.plusHours(72);
        };
    }
}
