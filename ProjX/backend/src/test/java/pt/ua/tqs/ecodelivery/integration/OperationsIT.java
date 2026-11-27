package pt.ua.tqs.ecodelivery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end checks of the company/courier registry, hub and bike maintenance, the usage session with
 * billing, and the maintenance tickets, through the real REST API and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperationsIT {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = new ObjectMapper();

    // ---------- helpers ----------

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               Object body) throws Exception {
        return mockMvc.perform(body == null ? req
                : req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private long createCompany(String name) throws Exception {
        return idOf(send(post("/api/companies"), map("name", name)).andExpect(status().isCreated()));
    }

    private long createCourier(String email, Long companyId) throws Exception {
        return idOf(send(post("/api/couriers"),
                map("name", "Courier", "email", email, "phone", "910", "companyId", companyId))
                .andExpect(status().isCreated()));
    }

    private long createHub(String name, int capacity) throws Exception {
        return idOf(send(post("/api/hubs"),
                map("name", name, "address", "Somewhere", "capacity", capacity,
                        "latitude", 40.64, "longitude", -8.65, "boundaryRadiusMeters", 50.0))
                .andExpect(status().isCreated()));
    }

    private long createBike(String serial, Long hubId) throws Exception {
        return idOf(send(post("/api/bikes"),
                map("serialNumber", serial, "model", "Cargo", "cargoCapacityKg", 50.0, "hubId", hubId,
                        "batteryLevel", 90, "status", "AVAILABLE"))
                .andExpect(status().isCreated()));
    }

    // ---------- tests ----------

    @Test
    @DisplayName("Companies and couriers can be registered, edited and listed, with duplicates and orphans refused")
    void companiesAndCouriers() throws Exception {
        long companyId = createCompany("ACME");

        send(post("/api/companies"), map("name", "acme")).andExpect(status().isConflict());
        send(post("/api/companies"), map("name", " ")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/companies/" + companyId)).andExpect(jsonPath("$.name", is("ACME")));
        mockMvc.perform(get("/api/companies")).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/companies/999")).andExpect(status().isNotFound());

        long other = createCompany("Other");
        send(put("/api/companies/" + companyId), map("name", "Other")).andExpect(status().isConflict());
        send(put("/api/companies/" + companyId), map("name", "ACME Lda")).andExpect(jsonPath("$.name", is("ACME Lda")));
        send(put("/api/companies/" + companyId), map("name", "ACME Lda")).andExpect(status().isOk());

        long courierId = createCourier("a@acme.pt", companyId);
        createCourier("free@x.pt", null);
        mockMvc.perform(get("/api/couriers")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/couriers?companyId=" + companyId)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eligible", is(true)));
        mockMvc.perform(get("/api/couriers/" + courierId)).andExpect(jsonPath("$.companyId", is((int) companyId)));
        mockMvc.perform(get("/api/couriers/999")).andExpect(status().isNotFound());

        send(post("/api/couriers"), map("name", "Dup", "email", "A@ACME.pt")).andExpect(status().isConflict());
        send(post("/api/couriers"), map("name", "Bad", "email", "not-an-email")).andExpect(status().isBadRequest());
        send(post("/api/couriers"), map("name", "Lost", "email", "l@x.pt", "companyId", 999))
                .andExpect(status().isNotFound());

        send(put("/api/couriers/" + courierId),
                map("name", "Renamed", "email", "a@acme.pt", "companyId", other))
                .andExpect(jsonPath("$.name", is("Renamed"))).andExpect(jsonPath("$.companyId", is((int) other)));
        send(put("/api/couriers/" + courierId), map("name", "Renamed", "email", "free@x.pt"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/companies/" + other)).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/companies/" + companyId)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Hubs can be edited and deleted, and bikes edited and relocated, within the hub capacities")
    void hubsAndBikesManagement() throws Exception {
        long small = createHub("Small", 1);
        long big = createHub("Big", 2);
        long bikeA = createBike("EB-A", small);
        long bikeB = createBike("EB-B", big);
        createBike("EB-C", big);

        send(put("/api/hubs/" + big), map("name", "Big", "address", "Somewhere", "capacity", 1,
                "latitude", 40.64, "longitude", -8.65)).andExpect(status().isConflict());
        send(put("/api/hubs/" + big), map("name", "Bigger", "address", "New street", "capacity", 5,
                "latitude", 40.65, "longitude", -8.66, "boundaryRadiusMeters", 80.0))
                .andExpect(jsonPath("$.name", is("Bigger"))).andExpect(jsonPath("$.capacity", is(5)))
                .andExpect(jsonPath("$.boundaryRadiusMeters", is(80.0)));
        send(put("/api/hubs/999"), map("name", "x", "address", "y", "capacity", 1)).andExpect(status().isNotFound());
        send(put("/api/hubs/" + big), map("name", "x", "address", "y", "capacity", 0)).andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/bikes/" + bikeA + "/hub?hubId=" + big)).andExpect(jsonPath("$.hubId", is((int) big)));
        mockMvc.perform(patch("/api/bikes/" + bikeA + "/hub?hubId=" + big)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/bikes/" + bikeB + "/hub?hubId=" + small)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/bikes/" + bikeA + "/hub?hubId=" + small)).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/bikes/" + bikeA + "/hub?hubId=999")).andExpect(status().isNotFound());

        send(put("/api/bikes/" + bikeA), map("model", "Cargo XL", "cargoCapacityKg", 80.0, "batteryLevel", 55))
                .andExpect(jsonPath("$.model", is("Cargo XL"))).andExpect(jsonPath("$.cargoCapacityKg", is(80.0)))
                .andExpect(jsonPath("$.batteryLevel", is(55)));
        send(put("/api/bikes/" + bikeA), map("model", " ")).andExpect(status().isOk());
        send(put("/api/bikes/" + bikeA), map("batteryLevel", 150)).andExpect(status().isConflict());
        send(put("/api/bikes/" + bikeA), map("cargoCapacityKg", 0)).andExpect(status().isConflict());
        send(put("/api/bikes/999"), map("model", "x")).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/hubs/" + big)).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/bikes/" + bikeB + "/hub?hubId=" + big)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/hubs/" + small)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/hubs/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A session is billed a deposit, ends at a hub with a refund, and shows in the filtered lists")
    void sessionLifecycleWithBilling() throws Exception {
        long companyId = createCompany("Billing Co");
        long courierId = createCourier("b@co.pt", companyId);
        long hubId = createHub("Hub", 3);
        long bikeId = createBike("EB-S", hubId);

        long reservationId = idOf(send(post("/api/reservations/immediate"),
                map("courierId", courierId, "bikeId", bikeId))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status", is("IN_USE"))));

        mockMvc.perform(get("/api/payments?courierId=" + courierId)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type", is("DEPOSIT"))).andExpect(jsonPath("$[0].status", is("SUCCEEDED")));

        mockMvc.perform(post("/api/reservations/" + reservationId + "/end?endHubId=999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/reservations/999/end?endHubId=" + hubId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/reservations/" + reservationId + "/end?endHubId=" + hubId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.endHubId", is((int) hubId)));
        mockMvc.perform(post("/api/reservations/" + reservationId + "/end?endHubId=" + hubId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/bikes/" + bikeId)).andExpect(jsonPath("$.status", is("AVAILABLE")))
                .andExpect(jsonPath("$.hubId", is((int) hubId)));

        String payments = mockMvc.perform(get("/api/payments?courierId=" + courierId))
                .andExpect(jsonPath("$", hasSize(2))).andReturn().getResponse().getContentAsString();
        long paymentId = json.readTree(payments).get(0).get("id").asLong();
        mockMvc.perform(get("/api/payments/" + paymentId)).andExpect(jsonPath("$.type", is("REFUND")))
                .andExpect(jsonPath("$.amount", is(10.0)));
        mockMvc.perform(get("/api/payments/999")).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/reservations?courierId=" + courierId + "&status=COMPLETED"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/reservations?courierId=" + courierId + "&status=CANCELLED"))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/reservations?status=COMPLETED")).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/reservations")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("A ticket takes the bike out of service until it is closed, and shows on the operator dashboard")
    void maintenanceTicketLifecycle() throws Exception {
        long hubId = createHub("Workshop", 2);
        long bikeId = createBike("EB-M", hubId);

        long ticketId = idOf(send(post("/api/maintenance/tickets"),
                map("bikeId", bikeId, "description", "Brake", "priority", "HIGH"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.priority", is("HIGH"))));
        mockMvc.perform(get("/api/bikes/" + bikeId)).andExpect(jsonPath("$.status", is("MAINTENANCE")));

        send(post("/api/maintenance/tickets"), map("bikeId", bikeId, "description", " ")).andExpect(status().isBadRequest());
        send(post("/api/maintenance/tickets"), map("description", "x")).andExpect(status().isBadRequest());
        send(post("/api/maintenance/tickets"), map("bikeId", 999, "description", "x")).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/operator/fleet-status")).andExpect(jsonPath("$.openTickets", is(1)))
                .andExpect(jsonPath("$.bikesByStatus.MAINTENANCE", is(1)));
        mockMvc.perform(get("/api/operator/tickets/open")).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/maintenance/tickets?status=OPEN")).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/maintenance/tickets")).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/maintenance/tickets/sla-breaches")).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/maintenance/tickets/" + ticketId)).andExpect(jsonPath("$.bikeId", is((int) bikeId)));
        mockMvc.perform(get("/api/maintenance/tickets/999")).andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/maintenance/tickets/" + ticketId + "/assign?technician=Rui"))
                .andExpect(jsonPath("$.status", is("ASSIGNED"))).andExpect(jsonPath("$.technician", is("Rui")));
        mockMvc.perform(post("/api/maintenance/tickets/" + ticketId + "/close?resolution=Fixed"))
                .andExpect(jsonPath("$.status", is("CLOSED"))).andExpect(jsonPath("$.resolution", is("Fixed")));
        mockMvc.perform(get("/api/bikes/" + bikeId)).andExpect(jsonPath("$.status", is("AVAILABLE")));

        mockMvc.perform(post("/api/maintenance/tickets/" + ticketId + "/close?resolution=Again"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/maintenance/tickets/" + ticketId + "/assign?technician=Rui"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/operator/fleet-status")).andExpect(jsonPath("$.openTickets", is(0)));
        mockMvc.perform(get("/api/maintenance/tickets?status=CLOSED")).andExpect(jsonPath("$[0].closedAt", startsWith("20")));
    }
}
