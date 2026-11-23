package pt.ua.tqs.ecodelivery.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;

/** Data access for Bike. Query methods are added with the stories that need them. */
@Repository
public interface BikeRepository extends JpaRepository<Bike, Long> {
    boolean existsBySerialNumber(String serialNumber);
    Optional<Bike> findBySerialNumber(String serialNumber);
    List<Bike> findByCurrentHubId(Long hubId);
    List<Bike> findByStatus(BikeStatus status);
    List<Bike> findByCurrentHubIdAndStatus(Long hubId, BikeStatus status);
    List<Bike> findByCurrentHubIdAndStatusAndActiveTrue(Long hubId, BikeStatus status);
    long countByCurrentHubId(Long hubId);
    long countByStatus(BikeStatus status);
}
