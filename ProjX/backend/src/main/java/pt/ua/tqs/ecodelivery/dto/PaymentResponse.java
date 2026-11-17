package pt.ua.tqs.ecodelivery.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import pt.ua.tqs.ecodelivery.model.PaymentStatus;
import pt.ua.tqs.ecodelivery.model.PaymentType;

/** A payment as returned by the API. */
public record PaymentResponse(
        Long id,
        Long reservationId,
        Long courierId,
        PaymentType type,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime createdAt) {
}
