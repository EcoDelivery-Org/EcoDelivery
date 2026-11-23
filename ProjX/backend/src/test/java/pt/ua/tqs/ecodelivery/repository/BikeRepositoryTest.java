package pt.ua.tqs.ecodelivery.repository;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Hub;

import static org.assertj.core.api.Assertions.assertThat;

/** Persistence slice test of the query behind the US08 availability discovery. */
@DataJpaTest
class BikeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BikeRepository bikeRepository;

    private Hub hub;
    private Hub otherHub;

    @BeforeEach
    void setUp() {
        hub = entityManager.persist(new Hub("Hub Rossio", "Rossio, Aveiro", 40.6412, -8.6536, 20, 50.0));
        otherHub = entityManager.persist(new Hub("Hub Campus", "Campus, Aveiro", 40.6300, -8.6580, 20, 50.0));
    }

    private Bike persistBike(String serial, BikeStatus status, boolean active, Hub at) {
        return entityManager.persist(new Bike(serial, "Urban Cargo", 50.0, 90, status, active, at));
    }

    @Test
    @DisplayName("findByCurrentHubIdAndStatusAndActiveTrue: excludes maintenance, retired and deactivated bikes (US08, BR2)")
    void availabilityQuery_excludesUnbookableBikes_BR2() {
        Bike available = persistBike("EB-OK", BikeStatus.AVAILABLE, true, hub);
        persistBike("EB-MAINT", BikeStatus.MAINTENANCE, true, hub);
        persistBike("EB-RETIRED", BikeStatus.RETIRED, true, hub);
        persistBike("EB-INACTIVE", BikeStatus.AVAILABLE, false, hub);
        entityManager.flush();

        List<Bike> result = bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(hub.getId(), BikeStatus.AVAILABLE);

        assertThat(result).containsExactly(available);
    }

    @Test
    @DisplayName("findByCurrentHubIdAndStatusAndActiveTrue: excludes bikes already reserved or in use (US08)")
    void availabilityQuery_excludesReservedAndInUseBikes() {
        Bike available = persistBike("EB-OK", BikeStatus.AVAILABLE, true, hub);
        persistBike("EB-RESERVED", BikeStatus.RESERVED, true, hub);
        persistBike("EB-IN-USE", BikeStatus.IN_USE, true, hub);
        entityManager.flush();

        List<Bike> result = bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(hub.getId(), BikeStatus.AVAILABLE);

        assertThat(result).containsExactly(available);
    }

    @Test
    @DisplayName("findByCurrentHubIdAndStatusAndActiveTrue: returns only bikes parked at the requested hub (US08)")
    void availabilityQuery_onlyBikesAtTheHub() {
        Bike here = persistBike("EB-HERE", BikeStatus.AVAILABLE, true, hub);
        persistBike("EB-THERE", BikeStatus.AVAILABLE, true, otherHub);
        persistBike("EB-NO-HUB", BikeStatus.AVAILABLE, true, null);
        entityManager.flush();

        List<Bike> result = bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(hub.getId(), BikeStatus.AVAILABLE);

        assertThat(result).containsExactly(here);
    }
}
