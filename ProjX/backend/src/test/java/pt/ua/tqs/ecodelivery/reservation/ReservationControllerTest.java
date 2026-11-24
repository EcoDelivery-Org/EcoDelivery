package pt.ua.tqs.ecodelivery.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.tqs.ecodelivery.dto.ReservationRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@DisplayName("ReservationController — REST API slice tests (US09)")
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Hub hub;
    private Courier courier;
    private Bike bike;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        hub = new Hub();
        hub.setName("Hub Central");
        StartFixtures.withId(hub, 1L);

        Company company = new Company();
        company.setName("Rapid Parcels");
        StartFixtures.withId(company, 5L);

        courier = new Courier();
        courier.setName("Courier Ana");
        courier.setCompany(company);
        StartFixtures.withId(courier, 10L);

        bike = new Bike();
        bike.setSerialNumber("EB-100");
        bike.setStatus(BikeStatus.RESERVED);
        bike.setCurrentHub(hub);
        StartFixtures.withId(bike, 100L);

        reservation = new Reservation();
        reservation.setCourier(courier);
        reservation.setBike(bike);
        reservation.setStartHub(hub);
        reservation.setPlannedStart(LocalDateTime.of(2026, 11, 23, 10, 0));
        reservation.setPlannedEnd(LocalDateTime.of(2026, 11, 23, 10, 15));
        reservation.setStatus(ReservationStatus.RESERVED);
        StartFixtures.withId(reservation, 1000L);
    }

    @Test
    @DisplayName("POST /api/reservations: creates reservation and returns 201 Created (US09)")
    void createReservation_returnsCreated() throws Exception {
        ReservationRequest req = new ReservationRequest(10L, 100L, null, null);

        when(sessionService.reserve(eq(10L), eq(100L), any(), any())).thenReturn(reservation);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1000)))
                .andExpect(jsonPath("$.courierId", is(10)))
                .andExpect(jsonPath("$.bikeId", is(100)))
                .andExpect(jsonPath("$.startHubId", is(1)))
                .andExpect(jsonPath("$.status", is("RESERVED")));
    }

    @Test
    @DisplayName("POST /api/reservations: conflict returns 409 Conflict")
    void createReservation_conflict_returns409() throws Exception {
        ReservationRequest req = new ReservationRequest(10L, 100L, null, null);

        when(sessionService.reserve(eq(10L), eq(100L), any(), any()))
                .thenThrow(new BusinessRuleException("Bike 100 is not available"));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", is("Bike 100 is not available")));
    }

    @Test
    @DisplayName("GET /api/reservations/{id}: returns 200 OK with reservation")
    void getReservation_returnsOk() throws Exception {
        when(sessionService.getReservation(1000L)).thenReturn(reservation);

        mockMvc.perform(get("/api/reservations/1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1000)))
                .andExpect(jsonPath("$.status", is("RESERVED")));
    }

    @Test
    @DisplayName("GET /api/reservations/{id}: unknown id returns 404 Not Found")
    void getReservation_notFound_returns404() throws Exception {
        when(sessionService.getReservation(999L))
                .thenThrow(new ResourceNotFoundException("Reservation 999 not found"));

        mockMvc.perform(get("/api/reservations/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("Reservation 999 not found")));
    }

    @Test
    @DisplayName("GET /api/reservations?courierId=10: returns list of courier reservations")
    void getCourierReservations_returnsList() throws Exception {
        when(sessionService.findByCourier(10L)).thenReturn(List.of(reservation));

        mockMvc.perform(get("/api/reservations?courierId=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1000)))
                .andExpect(jsonPath("$[0].courierId", is(10)));
    }

    @Test
    @DisplayName("POST /api/reservations/{id}/cancel: cancels reservation and returns 200 OK")
    void cancelReservation_returnsOk() throws Exception {
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(sessionService.cancel(1000L)).thenReturn(reservation);

        mockMvc.perform(post("/api/reservations/1000/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1000)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("POST /api/reservations/expire: triggers expiration and returns expired reservations")
    void expireHolds_returnsExpiredList() throws Exception {
        reservation.setStatus(ReservationStatus.EXPIRED);
        when(sessionService.expireHolds()).thenReturn(List.of(reservation));

        mockMvc.perform(post("/api/reservations/expire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("EXPIRED")));
    }
}
