package pt.ua.tqs.ecodelivery.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.MaintenanceTicket;
import pt.ua.tqs.ecodelivery.model.TicketStatus;

/** Data access for MaintenanceTicket. */
@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, Long> {

    List<MaintenanceTicket> findByStatusOrderByOpenedAtDesc(TicketStatus status);

    List<MaintenanceTicket> findByTechnicianOrderBySlaDeadlineAsc(String technician);

    List<MaintenanceTicket> findByTechnicianAndStatusOrderBySlaDeadlineAsc(String technician, TicketStatus status);

    List<MaintenanceTicket> findByStatusNotOrderBySlaDeadlineAsc(TicketStatus status);

    List<MaintenanceTicket> findAllByOrderByOpenedAtDesc();
}
