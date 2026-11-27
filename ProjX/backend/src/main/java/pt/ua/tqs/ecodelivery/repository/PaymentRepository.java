package pt.ua.tqs.ecodelivery.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.tqs.ecodelivery.model.Payment;
import pt.ua.tqs.ecodelivery.model.PaymentStatus;
import pt.ua.tqs.ecodelivery.model.PaymentType;

/** Data access for Payment. */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCourierIdOrderByCreatedAtDesc(Long courierId);

    List<Payment> findByReservationIdAndTypeAndStatus(Long reservationId, PaymentType type, PaymentStatus status);
}
