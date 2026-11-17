package pt.ua.tqs.ecodelivery.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.model.Payment;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.PaymentRepository;

/** Business logic for deposits, overtime charges and refunds. */
@Service
public class BillingService {

    private final PaymentRepository paymentRepository;
    private final PolicyService policyService;
    private final Clock clock;

    public BillingService(PaymentRepository paymentRepository, PolicyService policyService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.policyService = policyService;
        this.clock = clock;
    }

    public Payment chargeDeposit(Reservation reservation) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Payment refundDeposit(Reservation reservation) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Charges the time used beyond the reservation's planned end, if any. */
    public Payment chargeOvertime(Reservation reservation) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public BigDecimal calculateOvertimeFee(Duration overtime) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Payment getPayment(Long paymentId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<Payment> findByCourier(Long courierId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
