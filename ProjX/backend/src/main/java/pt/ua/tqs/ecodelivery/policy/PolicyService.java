package pt.ua.tqs.ecodelivery.policy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Rules of use defined by the institutional client. The values are read from application.yml
 * (ecodelivery.policy.*), so they can change without changing code.
 */
@Service
public class PolicyService {

    private final int minBatteryLevel;
    private final Duration maxReservationDuration;
    private final BigDecimal depositAmount;
    private final BigDecimal overtimeRatePerMinute;
    private final int maxActiveReservationsPerCourier;

    public PolicyService(@Value("${ecodelivery.policy.min-battery-level}") int minBatteryLevel,
                         @Value("${ecodelivery.policy.max-reservation-duration}") Duration maxReservationDuration,
                         @Value("${ecodelivery.policy.deposit-amount}") BigDecimal depositAmount,
                         @Value("${ecodelivery.policy.overtime-rate-per-minute}") BigDecimal overtimeRatePerMinute,
                         @Value("${ecodelivery.policy.max-active-reservations-per-courier}") int maxActiveReservationsPerCourier) {
        this.minBatteryLevel = minBatteryLevel;
        this.maxReservationDuration = maxReservationDuration;
        this.depositAmount = depositAmount;
        this.overtimeRatePerMinute = overtimeRatePerMinute;
        this.maxActiveReservationsPerCourier = maxActiveReservationsPerCourier;
    }

    public boolean isBatterySufficient(int batteryLevel) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Validates the start/end of a reservation (order, maximum duration). */
    public void validateReservationWindow(LocalDateTime start, LocalDateTime end) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean canCourierReserve(Long courierId, long activeReservations) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public int getMinBatteryLevel() {
        return minBatteryLevel;
    }

    public Duration getMaxReservationDuration() {
        return maxReservationDuration;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public BigDecimal getOvertimeRatePerMinute() {
        return overtimeRatePerMinute;
    }

    public int getMaxActiveReservationsPerCourier() {
        return maxActiveReservationsPerCourier;
    }
}
