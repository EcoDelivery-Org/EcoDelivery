package pt.ua.tqs.ecodelivery.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.dto.BikeRequest;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.model.BikeStatus;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HubAndVehicleInventoryIT {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("US01 Acceptance Flow: Provision hub and e-bikes, enforce capacity limit, verify real-time inventory logs")
    void fullInventoryProvisioningFlow() throws Exception {
        // 1. Operator provisions Hub with coordinates, capacity 2, and boundary radius 60m
        HubRequest hubReq = new HubRequest("Hub Central Aveiro", "Av. Dr. Lourenço Peixinho 100", 2, 40.6405, -8.6538, 60.0);

        String hubResponseStr = mockMvc.perform(post("/api/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hubReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Hub Central Aveiro")))
                .andExpect(jsonPath("$.capacity", is(2)))
                .andExpect(jsonPath("$.boundaryRadiusMeters", is(60.0)))
                .andExpect(jsonPath("$.availableCapacity", is(2)))
                .andReturn().getResponse().getContentAsString();

        Long hubId = objectMapper.readTree(hubResponseStr).get("id").asLong();

        // 2. Acceptance Criteria: provisioned hub immediately updates in system inventory logs
        mockMvc.perform(get("/api/inventory/logs?type=HUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].assetIdentifier", is("Hub Central Aveiro")))
                .andExpect(jsonPath("$[0].action", is("PROVISIONED")));

        // 3. Operator registers first E-Bike at this hub with serial/hardware attributes
        BikeRequest bike1 = new BikeRequest("EB-AVEIRO-01", "Urban Cargo Carrier", 50.0, hubId, 100, BikeStatus.AVAILABLE);

        mockMvc.perform(post("/api/bikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bike1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serialNumber", is("EB-AVEIRO-01")))
                .andExpect(jsonPath("$.model", is("Urban Cargo Carrier")))
                .andExpect(jsonPath("$.cargoCapacityKg", is(50.0)))
                .andExpect(jsonPath("$.hubId", is(hubId.intValue())));

        // 4. Operator registers second E-Bike at this hub (occupancy reaches maximum capacity: 2/2)
        BikeRequest bike2 = new BikeRequest("EB-AVEIRO-02", "Heavy Cargo Trike", 80.0, hubId, 95, BikeStatus.AVAILABLE);

        mockMvc.perform(post("/api/bikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bike2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serialNumber", is("EB-AVEIRO-02")));

        // 5. Hub capacity is now full: verify currentBikes is 2 and availableCapacity is 0
        mockMvc.perform(get("/api/hubs/" + hubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBikes", is(2)))
                .andExpect(jsonPath("$.availableCapacity", is(0)));

        // 6. Attempt to register a third bike at this full hub -> rejected with 409 Conflict (capacity rule enforced)
        BikeRequest bike3 = new BikeRequest("EB-AVEIRO-03", "Standard Cargo", 45.0, hubId, 100, BikeStatus.AVAILABLE);

        mockMvc.perform(post("/api/bikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bike3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("maximum capacity")));

        // 7. Acceptance Criteria: provisioned assets immediately update in system inventory logs
        mockMvc.perform(get("/api/inventory/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].assetType", is("E_BIKE")))
                .andExpect(jsonPath("$[0].assetIdentifier", is("EB-AVEIRO-02")))
                .andExpect(jsonPath("$[1].assetType", is("E_BIKE")))
                .andExpect(jsonPath("$[1].assetIdentifier", is("EB-AVEIRO-01")))
                .andExpect(jsonPath("$[2].assetType", is("HUB")))
                .andExpect(jsonPath("$[2].assetIdentifier", is("Hub Central Aveiro")));

        // 8. Verify bike inventory returns both registered bikes
        mockMvc.perform(get("/api/bikes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }
}
