package pt.ua.tqs.ecodelivery.maintenance;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Mock
    private MaintenanceTicketRepository ticketRepository;

    @Mock
    private BikeService bikeService;

    private MaintenanceService service;
    private Bike bike;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("UTC"));
        service = new MaintenanceService(ticketRepository, bikeService, clock);
        bike = new Bike("EB-1", "Cargo", 60, 80, BikeStatus.AVAILABLE, true, null);
        bike.setId(1L);
    }

    private void stubSave() {
        when(ticketRepository.save(any(MaintenanceTicket.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("US03: opening a ticket records category, SLA deadline and sends the bike to maintenance")
    void openTicketRecordsDefectAndSendsBikeToMaintenance() {
        when(bikeService.getBike(1L)).thenReturn(bike);
        stubSave();

        MaintenanceTicket ticket = service.openTicket(
                new TicketRequest(1L, " Battery swollen ", TicketPriority.HIGH, DefectCategory.BATTERY, null, null));

        assertThat(ticket.getBike()).isSameAs(bike);
        assertThat(ticket.getDescription()).isEqualTo("Battery swollen");
        assertThat(ticket.getDefectCategory()).isEqualTo(DefectCategory.BATTERY);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getOpenedAt()).isEqualTo(NOW);
        assertThat(ticket.getSlaDeadline()).isEqualTo(NOW.plusHours(4));
        assertThat(ticket.getTechnician()).isNull();
        verify(bikeService).changeStatus(1L, BikeStatus.MAINTENANCE);
    }

    @Test
    void defaultSlaDependsOnPriority() {
        when(bikeService.getBike(1L)).thenReturn(bike);
        stubSave();

        assertThat(service.openTicket(new TicketRequest(1L, "x", TicketPriority.MEDIUM, null, null, null))
                .getSlaDeadline()).isEqualTo(NOW.plusHours(24));
        assertThat(service.openTicket(new TicketRequest(1L, "x", TicketPriority.LOW, null, null, null))
                .getSlaDeadline()).isEqualTo(NOW.plusHours(72));
    }

    @Test
    void explicitTargetResolutionOverridesPriorityDefault() {
        when(bikeService.getBike(1L)).thenReturn(bike);
        stubSave();

        MaintenanceTicket ticket = service.openTicket(
                new TicketRequest(1L, "x", TicketPriority.LOW, DefectCategory.TIRES, 6, null));

        assertThat(ticket.getSlaDeadline()).isEqualTo(NOW.plusHours(6));
    }

    @Test
    void missingCategoryDefaultsToOther() {
        when(bikeService.getBike(1L)).thenReturn(bike);
        stubSave();

        assertThat(service.openTicket(new TicketRequest(1L, "x", TicketPriority.LOW, null, null, null))
                .getDefectCategory()).isEqualTo(DefectCategory.OTHER);
    }

    @Test
    @DisplayName("US03: a technician given at creation assigns the ticket immediately")
    void openTicketWithTechnicianIsAssigned() {
        when(bikeService.getBike(1L)).thenReturn(bike);
        stubSave();

        MaintenanceTicket ticket = service.openTicket(
                new TicketRequest(1L, "x", TicketPriority.HIGH, DefectCategory.SENSOR, null, " daniel "));

        assertThat(ticket.getTechnician()).isEqualTo("daniel");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void openTicketRejectsInvalidInput() {
        assertThatThrownBy(() -> service.openTicket(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.openTicket(new TicketRequest(null, "x", TicketPriority.LOW, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.openTicket(new TicketRequest(1L, " ", TicketPriority.LOW, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.openTicket(new TicketRequest(1L, "x", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.openTicket(new TicketRequest(1L, "x", TicketPriority.LOW, null, 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void openTicketForUnknownBikeFails() {
        when(bikeService.getBike(9L)).thenThrow(new ResourceNotFoundException("Bike not found with id: 9"));

        assertThatThrownBy(() -> service.openTicket(new TicketRequest(9L, "x", TicketPriority.LOW, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void openTicketForRetiredBikeFails() {
        bike.setStatus(BikeStatus.RETIRED);
        when(bikeService.getBike(1L)).thenReturn(bike);

        assertThatThrownBy(() -> service.openTicket(new TicketRequest(1L, "x", TicketPriority.LOW, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(bikeService, never()).changeStatus(any(), any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("US03: assigning a technician moves the ticket to ASSIGNED")
    void assignTechnicianSetsTechnicianAndStatus() {
        MaintenanceTicket ticket = new MaintenanceTicket();
        ticket.setStatus(TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        stubSave();

        MaintenanceTicket result = service.assignTechnician(5L, " daniel ");

        assertThat(result.getTechnician()).isEqualTo("daniel");
        assertThat(result.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void assignTechnicianCanReassign() {
        MaintenanceTicket ticket = new MaintenanceTicket();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setTechnician("ana");
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        stubSave();

        assertThat(service.assignTechnician(5L, "daniel").getTechnician()).isEqualTo("daniel");
    }

    @Test
    void assignTechnicianRejectsClosedBlankAndUnknown() {
        MaintenanceTicket closed = new MaintenanceTicket();
        closed.setStatus(TicketStatus.CLOSED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(closed));
        when(ticketRepository.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignTechnician(5L, "daniel")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.assignTechnician(5L, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignTechnician(6L, "daniel")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findTicketsPicksTheRightQuery() {
        List<MaintenanceTicket> list = List.of(new MaintenanceTicket());
        when(ticketRepository.findByTechnicianAndStatusOrderBySlaDeadlineAsc("d", TicketStatus.ASSIGNED)).thenReturn(list);
        when(ticketRepository.findByTechnicianOrderBySlaDeadlineAsc("d")).thenReturn(list);
        when(ticketRepository.findByStatusOrderByOpenedAtDesc(TicketStatus.OPEN)).thenReturn(list);
        when(ticketRepository.findAllByOrderByOpenedAtDesc()).thenReturn(list);

        assertThat(service.findTickets(TicketStatus.ASSIGNED, "d")).isSameAs(list);
        assertThat(service.findTickets(null, "d")).isSameAs(list);
        assertThat(service.findTickets(TicketStatus.OPEN, null)).isSameAs(list);
        assertThat(service.findTickets(null, " ")).isSameAs(list);
    }

    @Test
    void slaBreachesAreOpenTicketsPastDeadline() {
        MaintenanceTicket late = new MaintenanceTicket();
        late.setSlaDeadline(NOW.minusHours(1));
        MaintenanceTicket onTime = new MaintenanceTicket();
        onTime.setSlaDeadline(NOW.plusHours(1));
        when(ticketRepository.findByStatusNotOrderBySlaDeadlineAsc(TicketStatus.CLOSED)).thenReturn(List.of(late, onTime));

        assertThat(service.findSlaBreaches()).containsExactly(late);
        assertThat(service.findOpenTickets()).hasSize(2);
    }
}
