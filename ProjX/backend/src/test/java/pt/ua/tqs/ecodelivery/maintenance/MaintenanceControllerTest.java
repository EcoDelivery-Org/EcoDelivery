package pt.ua.tqs.ecodelivery.maintenance;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.DefectCategory;
import pt.ua.tqs.ecodelivery.model.MaintenanceTicket;
import pt.ua.tqs.ecodelivery.model.TicketPriority;
import pt.ua.tqs.ecodelivery.model.TicketStatus;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MaintenanceController.class)
class MaintenanceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MaintenanceService maintenanceService;

    private MaintenanceTicket ticket(TicketStatus status, String technician) {
        Bike bike = new Bike("EB-1", "Cargo", 60, 80, BikeStatus.MAINTENANCE, true, null);
        bike.setId(1L);
        MaintenanceTicket t = new MaintenanceTicket();
        t.setBike(bike);
        t.setDescription("Battery swollen");
        t.setDefectCategory(DefectCategory.BATTERY);
        t.setPriority(TicketPriority.HIGH);
        t.setStatus(status);
        t.setTechnician(technician);
        t.setOpenedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        t.setSlaDeadline(LocalDateTime.of(2026, 11, 20, 14, 0));
        return t;
    }

    @Test
    void openTicketReturnsCreatedTicket() throws Exception {
        when(maintenanceService.openTicket(any())).thenReturn(ticket(TicketStatus.ASSIGNED, "daniel"));

        mockMvc.perform(post("/api/maintenance/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":1,\"description\":\"Battery swollen\",\"priority\":\"HIGH\","
                                + "\"defectCategory\":\"BATTERY\",\"technician\":\"daniel\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bikeId", is(1)))
                .andExpect(jsonPath("$.defectCategory", is("BATTERY")))
                .andExpect(jsonPath("$.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.technician", is("daniel")))
                .andExpect(jsonPath("$.slaDeadline", is("2026-11-20T14:00:00")));
    }

    @Test
    void openTicketMapsBusinessRuleTo409() throws Exception {
        when(maintenanceService.openTicket(any())).thenThrow(new BusinessRuleException("Bike 1 is retired"));

        mockMvc.perform(post("/api/maintenance/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":1,\"description\":\"x\",\"priority\":\"LOW\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void openTicketMapsInvalidInputTo400() throws Exception {
        when(maintenanceService.openTicket(any())).thenThrow(new IllegalArgumentException("bad"));

        mockMvc.perform(post("/api/maintenance/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bikeId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void technicianFeedIsFilteredByTechnicianAndStatus() throws Exception {
        when(maintenanceService.findTickets(TicketStatus.ASSIGNED, "daniel"))
                .thenReturn(List.of(ticket(TicketStatus.ASSIGNED, "daniel")));

        mockMvc.perform(get("/api/maintenance/tickets?status=ASSIGNED&technician=daniel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].technician", is("daniel")));
    }

    @Test
    void assignEndpointReturnsAssignedTicket() throws Exception {
        when(maintenanceService.assignTechnician(3L, "daniel")).thenReturn(ticket(TicketStatus.ASSIGNED, "daniel"));

        mockMvc.perform(patch("/api/maintenance/tickets/3/assign?technician=daniel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ASSIGNED")));
    }
}
