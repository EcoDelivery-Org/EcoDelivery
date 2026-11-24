package pt.ua.tqs.ecodelivery.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
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
import pt.ua.tqs.ecodelivery.dto.ReservationRequest;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;
import pt.ua.tqs.ecodelivery.repository.ReservationRepository;
import pt.ua.tqs.ecodelivery.reservation.SessionService;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("US09 Integration Test: Temporal Vehicle Reservation and Hold Expiration")
class VehicleReservationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BikeRepository bikeRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Long hubId;
    private Long bikeId;
    private Long courier1Id;
    private Long courier2Id;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Provision Hub
        HubRequest hubReq = new HubRequest("Hub Central Aveiro", "Praça Humberto Delgado", 10, 40.6405, -8.6538, 50.0);
        String hubRes = mockMvc.perform(post("/api/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hubReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        hubId = objectMapper.readTree(hubRes).get("id").asLong();

        // 2. Register available E-Bike at the hub
        BikeRequest bikeReq = new BikeRequest("EB-RES-01", "City Cargo Pro", 60.0, hubId, 90, BikeStatus.AVAILABLE);
        String bikeRes = mockMvc.perform(post("/api/bikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bikeReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        bikeId = objectMapper.readTree(bikeRes).get("id").asLong();

        // 3. Register delivery company and 2 eligible couriers
        Company company = new Company();
        company.setName("EcoSpeed Logistics");
        entityManager.persist(company);

        Courier courier1 = new Courier();
        courier1.setName("Courier Bruno");
        courier1.setEmail("bruno@ecospeed.pt");
        courier1.setCompany(company);
        entityManager.persist(courier1);
        courier1Id = courier1.getId();

        Courier courier2 = new Courier();
        courier2.setName("Courier Carla");
        courier2.setEmail("carla@ecospeed.pt");
        courier2.setCompany(company);
        entityManager.persist(courier2);
        courier2Id = courier2.getId();

        entityManager.flush();
    }

    @Test
    @DisplayName("US09 Acceptance Flow: Temporal hold locks vehicle state for policy window; expired holds automatically return bike to active availability")
    void fullVehicleReservationAcceptanceFlow() throws Exception {
        // Step 1: Courier 1 places a temporal hold on the available e-bike while traveling to the hub
        ReservationRequest holdRequest = new ReservationRequest(courier1Id, bikeId, null, null);

        String reservationRes = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(holdRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId", is(courier1Id.intValue())))
                .andExpect(jsonPath("$.bikeId", is(bikeId.intValue())))
                .andExpect(jsonPath("$.status", is("RESERVED")))
                .andExpect(jsonPath("$.startHubId", is(hubId.intValue())))
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(reservationRes).get("id").asLong();

        // Step 2: Acceptance Criteria — Reservation locks vehicle state for the configured policy window
        mockMvc.perform(get("/api/bikes/" + bikeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESERVED")));

        mockMvc.perform(get("/api/bikes/" + bikeId + "/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(false)));

        // Step 3: Courier 2 attempts to reserve the same locked bike -> rejected with 409 Conflict
        ReservationRequest competingRequest = new ReservationRequest(courier2Id, bikeId, null, null);
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(competingRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("is not available")));

        // Step 4: Courier 1 cannot hold a second bike (active reservation policy DEC-4 enforced)
        BikeRequest bikeReq2 = new BikeRequest("EB-RES-02", "City Cargo Pro", 60.0, hubId, 85, BikeStatus.AVAILABLE);
        String bike2Res = mockMvc.perform(post("/api/bikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bikeReq2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bike2Id = objectMapper.readTree(bike2Res).get("id").asLong();

        ReservationRequest secondBikeRequest = new ReservationRequest(courier1Id, bike2Id, null, null);
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondBikeRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already has an active reservation")));

        // Step 5: Acceptance Criteria — Simulate hold window expiration
        // The policy window expires (courier did not start the session within the temporal hold window)
        Reservation currentRes = reservationRepository.findById(reservationId).orElseThrow();
        currentRes.setPlannedStart(LocalDateTime.now().minusMinutes(25));
        currentRes.setPlannedEnd(LocalDateTime.now().minusMinutes(10));
        reservationRepository.saveAndFlush(currentRes);

        // Expire holds triggers cleanup
        mockMvc.perform(post("/api/reservations/expire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(reservationId.intValue())))
                .andExpect(jsonPath("$[0].status", is("EXPIRED")));

        // Step 6: Acceptance Criteria — Expired hold automatically returns the vehicle to active availability
        mockMvc.perform(get("/api/bikes/" + bikeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AVAILABLE")));

        mockMvc.perform(get("/api/bikes/" + bikeId + "/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(true)));

        // Step 7: Courier 2 can now successfully reserve the bike
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(competingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId", is(courier2Id.intValue())))
                .andExpect(jsonPath("$.bikeId", is(bikeId.intValue())))
                .andExpect(jsonPath("$.status", is("RESERVED")));

        // Step 8: Vehicle state is locked again for Courier 2
        mockMvc.perform(get("/api/bikes/" + bikeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESERVED")));
    }
}
