package pt.ua.tqs.ecodelivery.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;

/**
 * The reservation queries used by US 10, in a JPA "slice".
 *
 * <p>{@code @DataJpaTest} starts only entities, repositories and an embedded H2 database; every
 * test runs in a transaction that is rolled back. The {@link TestEntityManager} writes the "given"
 * data directly, so the set-up does not depend on the repository methods under test.
 *
 * <p>Existing reservation used by most tests: bike B1, from 12:00 to 14:00.
 */
@DataJpaTest
@DisplayName("ReservationRepository — overlap (BR3, DEC-6) and active reservations (DEC-4)")
class ReservationRepositoryTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 23, 0, 0);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReservationRepository reservationRepository;

    private Hub hub;
    private Courier courier;
    private Bike bike;

    @BeforeEach
    void setUp() {
        hub = new Hub();
        hub.setName("Aveiro Centro");
        hub.setCapacity(10);
        entityManager.persist(hub);

        Company company = new Company();
        company.setName("Rapid Parcels");
        entityManager.persist(company);

        courier = new Courier();
        courier.setName("Ana");
        courier.setEmail("ana@rapidparcels.pt");
        courier.setCompany(company);
        entityManager.persist(courier);

        bike = newBike("ECO-0001");
    }

    private Bike newBike(String serialNumber) {
        Bike newBike = new Bike();
        newBike.setSerialNumber(serialNumber);
        newBike.setStatus(BikeStatus.AVAILABLE);
        newBike.setBatteryLevel(80);
        newBike.setActive(true);
        newBike.setCurrentHub(hub);
        return entityManager.persist(newBike);
    }

    private Reservation persistReservation(Bike reservedBike, int startHour, int endHour, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setCourier(courier);
        reservation.setBike(reservedBike);
        reservation.setStartHub(hub);
        reservation.setPlannedStart(DAY.plusHours(startHour));
        reservation.setPlannedEnd(DAY.plusHours(endHour));
        reservation.setStatus(status);
        return entityManager.persistAndFlush(reservation);
    }

    private static LocalDateTime at(String hhmm) {
        String[] parts = hhmm.split(":");
        return DAY.plusHours(Long.parseLong(parts[0])).plusMinutes(Long.parseLong(parts[1]));
    }

    // ---------------------------------------------------------------- BR3: overlap

    @ParameterizedTest(name = "[{0}, {1}] against [12:00, 14:00] -> conflict: {2}")
    @CsvSource({
            "10:00, 11:59, false",  // ends before the existing reservation starts
            "10:00, 12:00, true",   // BR3, boundary: ends exactly when the existing one starts
            "11:00, 13:00, true",   // partial overlap at the start
            "12:30, 13:30, true",   // inside
            "11:00, 15:00, true",   // covers it completely
            "13:00, 15:00, true",   // partial overlap at the end
            "14:00, 16:00, true",   // BR3, boundary: starts exactly when the existing one ends
            "14:01, 16:00, false"   // starts after the existing reservation ends
    })
    @DisplayName("BR3: a period conflicts with an existing reservation, boundaries included")
    void periodConflictsWithAnExistingReservation(String start, String end, boolean expected) {
        persistReservation(bike, 12, 14, ReservationStatus.RESERVED);

        assertEquals(expected, reservationRepository.existsConflictingReservation(bike.getId(), at(start), at(end)),
                "[" + start + ", " + end + "]");
    }

    @Test
    @DisplayName("BR3: a session in use also blocks the period")
    void sessionInUseConflicts() {
        persistReservation(bike, 12, 14, ReservationStatus.IN_USE);

        assertTrue(reservationRepository.existsConflictingReservation(bike.getId(), at("13:00"), at("15:00")));
    }

    @Test
    @DisplayName("BR3: cancelled and completed reservations do not block the period")
    void cancelledAndCompletedReservationsDoNotConflict() {
        persistReservation(bike, 12, 14, ReservationStatus.CANCELLED);
        persistReservation(bike, 12, 14, ReservationStatus.COMPLETED);

        assertFalse(reservationRepository.existsConflictingReservation(bike.getId(), at("12:00"), at("14:00")));
    }

    @Test
    @DisplayName("BR3: a reservation of another bike does not block the period")
    void reservationOfAnotherBikeDoesNotConflict() {
        Bike otherBike = newBike("ECO-0002");
        persistReservation(otherBike, 12, 14, ReservationStatus.RESERVED);

        assertFalse(reservationRepository.existsConflictingReservation(bike.getId(), at("12:00"), at("14:00")));
    }

    // ---------------------------------------------------------------- DEC-4: active reservations

    @Test
    @DisplayName("DEC-4: reservations and sessions in use count as active; cancelled and completed do not")
    void countsOnlyActiveReservationsOfTheCourier() {
        Bike otherBike = newBike("ECO-0002");
        persistReservation(bike, 8, 9, ReservationStatus.COMPLETED);
        persistReservation(bike, 9, 10, ReservationStatus.CANCELLED);
        persistReservation(bike, 12, 14, ReservationStatus.RESERVED);
        persistReservation(otherBike, 15, 16, ReservationStatus.IN_USE);

        long active = reservationRepository.countByCourierIdAndStatusIn(courier.getId(),
                List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE));

        assertEquals(2, active);
    }

    @Test
    @DisplayName("DEC-4: a courier with no reservations has no active reservations")
    void courierWithoutReservationsHasNone() {
        long active = reservationRepository.countByCourierIdAndStatusIn(courier.getId(),
                List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE));

        assertEquals(0, active);
    }
}
