package pt.ua.tqs.ecodelivery.payment;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.PaymentResponse;

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
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** GET /api/payments/{id} */
    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
