package pt.ua.tqs.ecodelivery.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.tqs.ecodelivery.maintenance.MaintenanceService;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.service.BikeService;
import pt.ua.tqs.ecodelivery.service.HubService;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperatorController.class)
class OperatorControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private BikeService bikeService;
    @MockitoBean private HubService hubService;
    @MockitoBean private MaintenanceService maintenanceService;

    @Test
    void activationReturnsUpdatedBike() throws Exception {
        Bike bike = new Bike("EB-1", "Cargo", 60, 80, BikeStatus.AVAILABLE, true, null);
        bike.setId(1L);
        when(bikeService.activate(1L)).thenReturn(bike);
        mockMvc.perform(post("/api/operator/bikes/1/activate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void deactivationReturnsUpdatedBike() throws Exception {
        Bike bike = new Bike("EB-1", "Cargo", 60, 80, BikeStatus.AVAILABLE, false, null);
        bike.setId(1L);
        when(bikeService.deactivate(1L)).thenReturn(bike);
        mockMvc.perform(post("/api/operator/bikes/1/deactivate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active", is(false)));
    }
}
