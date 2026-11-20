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
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.service.HubService;

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
}
