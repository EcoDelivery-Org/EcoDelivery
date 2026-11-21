package pt.ua.tqs.ecodelivery.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.BikeEvent;

/** Data access for BikeEvent. Query methods are added with the stories that need them. */
@Repository
public interface BikeEventRepository extends JpaRepository<BikeEvent, Long> {
    List<BikeEvent> findByBikeIdOrderByTimestampDesc(Long bikeId);
    List<BikeEvent> findAllByOrderByTimestampDesc();
}
