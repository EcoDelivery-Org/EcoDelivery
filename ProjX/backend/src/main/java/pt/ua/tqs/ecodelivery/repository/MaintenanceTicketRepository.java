package pt.ua.tqs.ecodelivery.repository;

import pt.ua.tqs.ecodelivery.model.MaintenanceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for MaintenanceTicket. Query methods are added with the stories that need them. */
@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, Long> {
}
