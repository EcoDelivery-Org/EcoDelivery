package pt.ua.tqs.ecodelivery.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;

/** Data access for Reservation. Query methods are added with the stories that need them. */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByBikeIdAndStatus(Long bikeId, ReservationStatus status);

    /**
     * BR3: whether the bike has an active reservation (RESERVED or IN_USE) overlapping
     * {@code [start, end]}. Both boundaries are included: a period that starts exactly when another
     * ends, or ends exactly when another starts, overlaps.
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.bike.id = :bikeId
              and r.status in (pt.ua.tqs.ecodelivery.model.ReservationStatus.RESERVED,
                               pt.ua.tqs.ecodelivery.model.ReservationStatus.IN_USE)
              and r.plannedStart <= :end
              and :start <= r.plannedEnd
            """)
    boolean existsConflictingReservation(@Param("bikeId") Long bikeId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /** DEC-4: number of the courier's reservations in the given states. */
    long countByCourierIdAndStatusIn(Long courierId, Collection<ReservationStatus> statuses);
}
