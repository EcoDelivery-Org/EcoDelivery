package pt.ua.tqs.ecodelivery.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.MaintenanceTicket;
import pt.ua.tqs.ecodelivery.model.TicketPriority;
import pt.ua.tqs.ecodelivery.model.TicketStatus;
import pt.ua.tqs.ecodelivery.repository.MaintenanceTicketRepository;
import pt.ua.tqs.ecodelivery.service.BikeService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 10, 0);

    @Mock
    private MaintenanceTicketRepository ticketRepository;
    @Mock
    private BikeService bikeService;

    private MaintenanceService service;
    private Bike bike;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new MaintenanceService(ticketRepository, bikeService, clock);
        bike = new Bike();
        bike.setId(7L);
        bike.setStatus(BikeStatus.AVAILABLE);
        when(bikeService.getBike(7L)).thenReturn(bike);
        when(ticketRepository.save(any(MaintenanceTicket.class))).then(returnsFirstArg());
    }

    private MaintenanceTicket ticket(TicketStatus status, LocalDateTime sla) {
        MaintenanceTicket t = new MaintenanceTicket();
        t.setBike(bike);
        t.setStatus(status);
        t.setSlaDeadline(sla);
        return t;
    }

    @Test
    @DisplayName("Opening a ticket sends the bike to maintenance and sets the SLA from the priority")
    void openTicket() {
        MaintenanceTicket high = service.openTicket(7L, " Brake ", TicketPriority.HIGH);
        MaintenanceTicket medium = service.openTicket(7L, "Chain", null);
        MaintenanceTicket low = service.openTicket(7L, "Paint", TicketPriority.LOW);

        assertThat(high.getDescription()).isEqualTo("Brake");
        assertThat(high.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(high.getOpenedAt()).isEqualTo(NOW);
        assertThat(high.getSlaDeadline()).isEqualTo(NOW.plusHours(4));
        assertThat(medium.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(medium.getSlaDeadline()).isEqualTo(NOW.plusHours(24));
        assertThat(low.getSlaDeadline()).isEqualTo(NOW.plusHours(72));
        verify(bikeService, org.mockito.Mockito.times(3)).changeStatus(7L, BikeStatus.MAINTENANCE);
    }

    @Test
    @DisplayName("A ticket needs a bike and a description")
    void openTicketValidation() {
        assertThatThrownBy(() -> service.openTicket(null, "x", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.openTicket(7L, " ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.openTicket(7L, null, null)).isInstanceOf(IllegalArgumentException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Assigning a technician marks the ticket as assigned")
    void assign() {
        MaintenanceTicket t = ticket(TicketStatus.OPEN, NOW);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(t));

        MaintenanceTicket result = service.assignTechnician(1L, " Rui ");

        assertThat(result.getTechnician()).isEqualTo("Rui");
        assertThat(result.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    @DisplayName("Assigning needs a technician and an open ticket")
    void assignValidation() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.CLOSED, NOW)));

        assertThatThrownBy(() -> service.assignTechnician(1L, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignTechnician(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignTechnician(1L, "Rui")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Closing the only open ticket makes the bike available again")
    void close() {
        bike.setStatus(BikeStatus.MAINTENANCE);
        MaintenanceTicket t = ticket(TicketStatus.ASSIGNED, NOW);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(t));
        when(ticketRepository.existsByBikeIdAndStatusNot(7L, TicketStatus.CLOSED)).thenReturn(false);

        MaintenanceTicket result = service.closeTicket(1L, " Fixed ");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(result.getResolution()).isEqualTo("Fixed");
        assertThat(result.getClosedAt()).isEqualTo(NOW);
        verify(bikeService).changeStatus(7L, BikeStatus.AVAILABLE);
    }

    @Test
    @DisplayName("The bike stays in maintenance while it has other open tickets, or when it is not in maintenance")
    void closeKeepsBikeOut() {
        bike.setStatus(BikeStatus.MAINTENANCE);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, NOW)));
        when(ticketRepository.existsByBikeIdAndStatusNot(7L, TicketStatus.CLOSED)).thenReturn(true);
        service.closeTicket(1L, "Fixed");

        bike.setStatus(BikeStatus.RETIRED);
        when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, NOW)));
        when(ticketRepository.existsByBikeIdAndStatusNot(7L, TicketStatus.CLOSED)).thenReturn(false);
        service.closeTicket(2L, "Fixed");

        verify(bikeService, never()).changeStatus(7L, BikeStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Closing needs a resolution and a ticket that is not closed yet")
    void closeValidation() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.CLOSED, NOW)));

        assertThatThrownBy(() -> service.closeTicket(1L, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.closeTicket(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.closeTicket(1L, "Done")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("An unknown ticket is reported")
    void unknownTicket() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTicket(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tickets can be listed with or without a status filter; open ones exclude closed")
    void listing() {
        MaintenanceTicket t = ticket(TicketStatus.OPEN, NOW);
        when(ticketRepository.findAll()).thenReturn(List.of(t));
        when(ticketRepository.findByStatus(TicketStatus.OPEN)).thenReturn(List.of(t));
        when(ticketRepository.findByStatusNot(TicketStatus.CLOSED)).thenReturn(List.of(t));

        assertThat(service.findTickets(null)).containsExactly(t);
        assertThat(service.findTickets(TicketStatus.OPEN)).containsExactly(t);
        assertThat(service.findOpenTickets()).containsExactly(t);
    }

    @Test
    @DisplayName("SLA breaches are open tickets whose deadline has passed")
    void slaBreaches() {
        MaintenanceTicket late = ticket(TicketStatus.OPEN, NOW.minusMinutes(1));
        MaintenanceTicket onTime = ticket(TicketStatus.OPEN, NOW.plusHours(1));
        MaintenanceTicket noDeadline = ticket(TicketStatus.OPEN, null);
        when(ticketRepository.findByStatusNot(TicketStatus.CLOSED)).thenReturn(List.of(late, onTime, noDeadline));

        assertThat(service.findSlaBreaches()).containsExactly(late);
    }
}
