package pt.ua.tqs.ecodelivery.repository;

import pt.ua.tqs.ecodelivery.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for Reservation. Query methods are added with the stories that need them. */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
