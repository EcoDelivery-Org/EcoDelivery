package pt.ua.tqs.ecodelivery.payment;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.PaymentResponse;
import pt.ua.tqs.ecodelivery.model.Payment;

/** REST boundary for payments (epic: Admin e Pagamentos). */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final BillingService billingService;

    public PaymentController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** GET /api/payments?courierId= */
    @GetMapping
    public List<PaymentResponse> getPayments(@RequestParam Long courierId) {
        return billingService.findByCourier(courierId).stream().map(this::toResponse).toList();
    }

    /** GET /api/payments/{id} */
    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return toResponse(billingService.getPayment(id));
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(),
                p.getReservation() != null ? p.getReservation().getId() : null,
                p.getCourier() != null ? p.getCourier().getId() : null,
                p.getType(), p.getAmount(), p.getStatus(), p.getCreatedAt());
    }
}
