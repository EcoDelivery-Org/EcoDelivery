package pt.ua.tqs.ecodelivery.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.Courier;

/** Data access for Courier. */
@Repository
public interface CourierRepository extends JpaRepository<Courier, Long> {
    List<Courier> findByCompanyId(Long companyId);

    boolean existsByEmailIgnoreCase(String email);
}
