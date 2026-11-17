package pt.ua.tqs.ecodelivery.repository;

import pt.ua.tqs.ecodelivery.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for Payment. Query methods are added with the stories that need them. */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
