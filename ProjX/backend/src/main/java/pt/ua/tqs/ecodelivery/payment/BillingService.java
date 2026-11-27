package pt.ua.tqs.ecodelivery.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.PaymentStatus;
import pt.ua.tqs.ecodelivery.model.PaymentType;
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

    /** BR8: charges the security deposit. The gateway is simulated and always accepts. */
    @Transactional
    public Payment chargeDeposit(Reservation reservation) {
        return record(reservation, PaymentType.DEPOSIT, policyService.getDepositAmount());
    }

    /** Refunds the deposit that was charged for the reservation; {@code null} if none was charged. */
    @Transactional
    public Payment refundDeposit(Reservation reservation) {
        return paymentRepository
                .findByReservationIdAndTypeAndStatus(reservation.getId(), PaymentType.DEPOSIT, PaymentStatus.SUCCEEDED)
                .stream().findFirst()
                .map(deposit -> record(reservation, PaymentType.REFUND, deposit.getAmount()))
                .orElse(null);
    }

    /** Charges the time used beyond the reservation's planned end, if any. */
    @Transactional
    public Payment chargeOvertime(Reservation reservation) {
        if (reservation.getPlannedEnd() == null || reservation.getActualEnd() == null) {
            return null;
        }
        Duration overtime = Duration.between(reservation.getPlannedEnd().atZone(clock.getZone()),
                reservation.getActualEnd().atZone(clock.getZone()));
        BigDecimal fee = calculateOvertimeFee(overtime);
        if (fee.signum() <= 0) {
            return null;
        }
        return record(reservation, PaymentType.OVERTIME, fee);
    }

    /** Overtime fee: each started minute is billed at the configured rate. */
    public BigDecimal calculateOvertimeFee(Duration overtime) {
        if (overtime == null || overtime.isNegative() || overtime.isZero()) {
            return BigDecimal.ZERO.setScale(2);
        }
        long minutes = overtime.toMinutes() + (overtime.toSecondsPart() > 0 || overtime.toNanosPart() > 0 ? 1 : 0);
        return policyService.getOvertimeRatePerMinute().multiply(BigDecimal.valueOf(minutes))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
    }

    public List<Payment> findByCourier(Long courierId) {
        return paymentRepository.findByCourierIdOrderByCreatedAtDesc(courierId);
    }

    private Payment record(Reservation reservation, PaymentType type, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setCourier(reservation.getCourier());
        payment.setType(type);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setGatewayReference("SIM-" + UUID.randomUUID());
        payment.setCreatedAt(LocalDateTime.now(clock));
        return paymentRepository.save(payment);
    }
}
