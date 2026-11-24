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
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.service.HubService;
import pt.ua.tqs.ecodelivery.service.NearbyHub;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HubController.class)
class HubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private HubService hubService;

    @Test
    @DisplayName("POST /api/hubs - create hub returns 201 Created (US01)")
    void createHub_returnsCreated() throws Exception {
        HubRequest request = new HubRequest("Hub Rossio", "Rossio, Aveiro", 15, 40.6412, -8.6536, 60.0);
        Hub hub = new Hub("Hub Rossio", "Rossio, Aveiro", 40.6412, -8.6536, 15, 60.0);
        hub.setId(1L);

        when(hubService.createHub(any(HubRequest.class))).thenReturn(hub);
        when(hubService.getBikeCount(1L)).thenReturn(0L);

        mockMvc.perform(post("/api/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Hub Rossio")))
                .andExpect(jsonPath("$.capacity", is(15)))
                .andExpect(jsonPath("$.boundaryRadiusMeters", is(60.0)))
                .andExpect(jsonPath("$.availableCapacity", is(15)));
    }

    @Test
    @DisplayName("GET /api/hubs - list all provisioned hubs returns 200 OK (US01)")
    void getHubs_returnsList() throws Exception {
        Hub hub = new Hub("Hub Rossio", "Rossio, Aveiro", 40.6412, -8.6536, 15, 60.0);
        hub.setId(1L);

        when(hubService.findAll()).thenReturn(List.of(hub));
        when(hubService.getBikeCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Hub Rossio")))
                .andExpect(jsonPath("$[0].currentBikes", is(3)))
                .andExpect(jsonPath("$[0].availableCapacity", is(12)));
    }

    @Test
    @DisplayName("GET /api/hubs/{id} - get hub by id returns 200 OK (US01)")
    void getHub_returnsHub() throws Exception {
        Hub hub = new Hub("Hub Rossio", "Rossio, Aveiro", 40.6412, -8.6536, 10, 50.0);
        hub.setId(1L);

        when(hubService.getHub(1L)).thenReturn(hub);
        when(hubService.getBikeCount(1L)).thenReturn(2L);

        mockMvc.perform(get("/api/hubs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Hub Rossio")));
    }

    // ----------------- US08: Hub Search & Availability Discovery -----------------

    @Test
    @DisplayName("GET /api/hubs/{id}/bikes - returns the bookable bikes with their battery levels (US08)")
    void getAvailableBikes_returnsBikes() throws Exception {
        Hub hub = new Hub("Hub Rossio", "Rossio, Aveiro", 40.6412, -8.6536, 10, 50.0);
        hub.setId(1L);
        Bike bike = new Bike("EB-AVR-001", "Urban Cargo", 50.0, 85, BikeStatus.AVAILABLE, true, hub);
        bike.setId(7L);
        when(hubService.getAvailableBikes(1L)).thenReturn(List.of(bike));

        mockMvc.perform(get("/api/hubs/1/bikes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(7)))
                .andExpect(jsonPath("$[0].serialNumber", is("EB-AVR-001")))
                .andExpect(jsonPath("$[0].batteryLevel", is(85)))
                .andExpect(jsonPath("$[0].status", is("AVAILABLE")))
                .andExpect(jsonPath("$[0].hubId", is(1)));
    }

    @Test
    @DisplayName("GET /api/hubs/{id}/bikes - unknown hub returns 404 Not Found (US08)")
    void getAvailableBikes_unknownHub_returnsNotFound() throws Exception {
        when(hubService.getAvailableBikes(99L)).thenThrow(new ResourceNotFoundException("Hub not found with id: 99"));

        mockMvc.perform(get("/api/hubs/99/bikes"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/hubs/nearby - returns hubs with distance and available bikes (US08)")
    void findNearbyHubs_returnsHubsWithDistanceAndAvailability() throws Exception {
        Hub hub = new Hub("Hub Rossio", "Rossio, Aveiro", 40.6412, -8.6536, 10, 50.0);
        hub.setId(1L);
        when(hubService.findNearbyHubs("Rossio, Aveiro", 2.0)).thenReturn(List.of(new NearbyHub(hub, 1.23456, 3)));

        mockMvc.perform(get("/api/hubs/nearby")
                        .param("address", "Rossio, Aveiro")
                        .param("radiusKm", "2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("Hub Rossio")))
                .andExpect(jsonPath("$[0].distanceKm", is(1.23)))
                .andExpect(jsonPath("$[0].availableBikes", is(3)));
    }

    @Test
    @DisplayName("GET /api/hubs/nearby - invalid search parameters return 400 Bad Request (US08)")
    void findNearbyHubs_invalidParameters_returnsBadRequest() throws Exception {
        when(hubService.findNearbyHubs("Rossio, Aveiro", 0.0))
                .thenThrow(new IllegalArgumentException("Search radius must be greater than zero"));

        mockMvc.perform(get("/api/hubs/nearby")
                        .param("address", "Rossio, Aveiro")
                        .param("radiusKm", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/hubs/nearby - missing radius returns 400 Bad Request (US08)")
    void findNearbyHubs_missingRadius_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/hubs/nearby").param("address", "Rossio, Aveiro"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/hubs/nearby - address that cannot be geocoded returns 409 Conflict (US08)")
    void findNearbyHubs_geocodingFails_returnsConflict() throws Exception {
        when(hubService.findNearbyHubs("Nowhere", 2.0))
                .thenThrow(new BusinessRuleException("Unable to geocode address: Nowhere"));

        mockMvc.perform(get("/api/hubs/nearby")
                        .param("address", "Nowhere")
                        .param("radiusKm", "2.0"))
                .andExpect(status().isConflict());
    }
}
