package pt.ua.tqs.ecodelivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Payment;
import pt.ua.tqs.ecodelivery.model.PaymentStatus;
import pt.ua.tqs.ecodelivery.model.PaymentType;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 10, 0);

    @Mock
    private PaymentRepository paymentRepository;

    private BillingService billing;

    @BeforeEach
    void setUp() {
        PolicyService policy = new PolicyService(20, Duration.ofHours(6), new BigDecimal("10.00"),
                new BigDecimal("0.10"), 1, Duration.ofMinutes(15));
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        billing = new BillingService(paymentRepository, policy, clock);
        when(paymentRepository.save(any(Payment.class))).then(returnsFirstArg());
    }

    private Reservation reservation(LocalDateTime plannedEnd, LocalDateTime actualEnd) {
        Reservation r = new Reservation();
        ReflectionTestUtils.setField(r, "id", 5L);
        r.setCourier(new Courier());
        r.setPlannedEnd(plannedEnd);
        r.setActualEnd(actualEnd);
        return r;
    }

    @Test
    @DisplayName("The deposit is charged at the configured amount and recorded as succeeded")
    void chargeDeposit() {
        Payment p = billing.chargeDeposit(reservation(NOW, null));

        assertThat(p.getType()).isEqualTo(PaymentType.DEPOSIT);
        assertThat(p.getAmount()).isEqualByComparingTo("10.00");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(p.getGatewayReference()).startsWith("SIM-");
        assertThat(p.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Refund repeats the amount of the deposit that was charged")
    void refundDeposit() {
        Payment deposit = new Payment();
        deposit.setAmount(new BigDecimal("10.00"));
        when(paymentRepository.findByReservationIdAndTypeAndStatus(5L, PaymentType.DEPOSIT, PaymentStatus.SUCCEEDED))
                .thenReturn(List.of(deposit));

        Payment refund = billing.refundDeposit(reservation(NOW, null));

        assertThat(refund.getType()).isEqualTo(PaymentType.REFUND);
        assertThat(refund.getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("No refund when no deposit was charged")
    void refundWithoutDeposit() {
        when(paymentRepository.findByReservationIdAndTypeAndStatus(5L, PaymentType.DEPOSIT, PaymentStatus.SUCCEEDED))
                .thenReturn(List.of());

        assertThat(billing.refundDeposit(reservation(NOW, null))).isNull();
    }

    @Test
    @DisplayName("Overtime is charged per started minute")
    void chargeOvertime() {
        Payment p = billing.chargeOvertime(reservation(NOW, NOW.plusMinutes(30).plusSeconds(1)));

        assertThat(p.getType()).isEqualTo(PaymentType.OVERTIME);
        assertThat(p.getAmount()).isEqualByComparingTo("3.10");
    }

    @Test
    @DisplayName("No overtime charge when the bike came back on time or the session has no end")
    void noOvertime() {
        assertThat(billing.chargeOvertime(reservation(NOW, NOW.minusMinutes(5)))).isNull();
        assertThat(billing.chargeOvertime(reservation(NOW, NOW))).isNull();
        assertThat(billing.chargeOvertime(reservation(NOW, null))).isNull();
        assertThat(billing.chargeOvertime(reservation(null, NOW))).isNull();
    }

    @Test
    @DisplayName("Overtime fee: zero for none, exact minutes and rounded up for started minutes")
    void overtimeFee() {
        assertThat(billing.calculateOvertimeFee(null)).isEqualByComparingTo("0");
        assertThat(billing.calculateOvertimeFee(Duration.ZERO)).isEqualByComparingTo("0");
        assertThat(billing.calculateOvertimeFee(Duration.ofMinutes(-3))).isEqualByComparingTo("0");
        assertThat(billing.calculateOvertimeFee(Duration.ofMinutes(10))).isEqualByComparingTo("1.00");
        assertThat(billing.calculateOvertimeFee(Duration.ofSeconds(61))).isEqualByComparingTo("0.20");
    }

    @Test
    @DisplayName("A payment is found by id, and an unknown id is reported")
    void getPayment() {
        Payment payment = new Payment();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(billing.getPayment(1L)).isSameAs(payment);
        assertThatThrownBy(() -> billing.getPayment(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Payments of a courier come from the repository")
    void findByCourier() {
        Payment payment = new Payment();
        when(paymentRepository.findByCourierIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(payment));

        assertThat(billing.findByCourier(9L)).containsExactly(payment);
    }
}
