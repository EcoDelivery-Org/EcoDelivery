package pt.ua.tqs.ecodelivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.tqs.ecodelivery.dto.BikeRequest;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.service.BikeService;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BikeController.class)
class BikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private BikeService bikeService;

    @Test
    @DisplayName("POST /api/bikes - create bike returns 201 Created (US01)")
    void createBike_returnsCreated() throws Exception {
        BikeRequest request = new BikeRequest("EB-500", "CargoMax 500", 65.0, 1L, 100, BikeStatus.AVAILABLE);
        Hub hub = new Hub("Hub Rossio", "Rossio", 40.64, -8.65, 10, 50.0);
        hub.setId(1L);
        Bike bike = new Bike("EB-500", "CargoMax 500", 65.0, 100, BikeStatus.AVAILABLE, true, hub);
        bike.setId(500L);

        when(bikeService.registerBike(any(BikeRequest.class))).thenReturn(bike);

        mockMvc.perform(post("/api/bikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(500)))
                .andExpect(jsonPath("$.serialNumber", is("EB-500")))
                .andExpect(jsonPath("$.model", is("CargoMax 500")))
                .andExpect(jsonPath("$.cargoCapacityKg", is(65.0)))
                .andExpect(jsonPath("$.hubId", is(1)));
    }

    @Test
    @DisplayName("GET /api/bikes - returns list of bikes (US01)")
    void getBikes_returnsList() throws Exception {
        Hub hub = new Hub("Hub Rossio", "Rossio", 40.64, -8.65, 10, 50.0);
        hub.setId(1L);
        Bike bike = new Bike("EB-500", "CargoMax 500", 65.0, 100, BikeStatus.AVAILABLE, true, hub);
        bike.setId(500L);

        when(bikeService.findBikes(null, null)).thenReturn(List.of(bike));

        mockMvc.perform(get("/api/bikes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serialNumber", is("EB-500")));
    }

    @Test
    @DisplayName("GET /api/bikes/{id} - returns bike by id (US01)")
    void getBike_returnsBike() throws Exception {
        Hub hub = new Hub("Hub Rossio", "Rossio", 40.64, -8.65, 10, 50.0);
        hub.setId(1L);
        Bike bike = new Bike("EB-500", "CargoMax 500", 65.0, 100, BikeStatus.AVAILABLE, true, hub);
        bike.setId(500L);

        when(bikeService.getBike(500L)).thenReturn(bike);

        mockMvc.perform(get("/api/bikes/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(500)))
                .andExpect(jsonPath("$.serialNumber", is("EB-500")));
    }
}
