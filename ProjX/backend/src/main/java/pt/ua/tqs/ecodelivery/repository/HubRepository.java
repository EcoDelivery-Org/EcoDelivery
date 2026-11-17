package pt.ua.tqs.ecodelivery.repository;

import pt.ua.tqs.ecodelivery.model.Hub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for Hub. Query methods are added with the stories that need them. */
@Repository
public interface HubRepository extends JpaRepository<Hub, Long> {
}
