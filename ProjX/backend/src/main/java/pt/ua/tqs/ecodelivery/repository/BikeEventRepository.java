package pt.ua.tqs.ecodelivery.repository;

import pt.ua.tqs.ecodelivery.model.BikeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for BikeEvent. Query methods are added with the stories that need them. */
@Repository
public interface BikeEventRepository extends JpaRepository<BikeEvent, Long> {
}
