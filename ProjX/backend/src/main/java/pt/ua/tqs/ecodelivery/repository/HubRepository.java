package pt.ua.tqs.ecodelivery.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.Hub;

/** Data access for Hub. Query methods are added with the stories that need them. */
@Repository
public interface HubRepository extends JpaRepository<Hub, Long> {
    boolean existsByName(String name);
    Optional<Hub> findByName(String name);
}
