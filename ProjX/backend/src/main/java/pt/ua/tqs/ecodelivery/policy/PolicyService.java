package pt.ua.tqs.ecodelivery.policy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.model.Courier;

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
    private final Duration startWindowTolerance;
    private final Duration holdDuration;

    @Autowired
    public PolicyService(@Value("${ecodelivery.policy.min-battery-level}") int minBatteryLevel,
                         @Value("${ecodelivery.policy.max-reservation-duration}") Duration maxReservationDuration,
                         @Value("${ecodelivery.policy.deposit-amount}") BigDecimal depositAmount,
                         @Value("${ecodelivery.policy.overtime-rate-per-minute}") BigDecimal overtimeRatePerMinute,
                         @Value("${ecodelivery.policy.max-active-reservations-per-courier}") int maxActiveReservationsPerCourier,
                         @Value("${ecodelivery.policy.start-window}") Duration startWindowTolerance,
                         @Value("${ecodelivery.policy.hold-duration:PT15M}") Duration holdDuration) {
        this.minBatteryLevel = minBatteryLevel;
        this.maxReservationDuration = maxReservationDuration;
        this.depositAmount = depositAmount;
        this.overtimeRatePerMinute = overtimeRatePerMinute;
        this.maxActiveReservationsPerCourier = maxActiveReservationsPerCourier;
        this.startWindowTolerance = startWindowTolerance;
        this.holdDuration = holdDuration;
    }

    public PolicyService(int minBatteryLevel, Duration maxReservationDuration, BigDecimal depositAmount,
                         BigDecimal overtimeRatePerMinute, int maxActiveReservationsPerCourier,
                         Duration startWindowTolerance) {
        this(minBatteryLevel, maxReservationDuration, depositAmount, overtimeRatePerMinute,
                maxActiveReservationsPerCourier, startWindowTolerance, Duration.ofMinutes(15));
    }

    /** BR1: the battery must be at or above the minimum level. */
    public boolean isBatterySufficient(int batteryLevel) {
        return batteryLevel >= minBatteryLevel;
    }

    /** BR4/DEC-5: a reservation can be started from {@code tolerance} before to {@code tolerance} after its planned start. */
    public boolean isWithinStartWindow(LocalDateTime plannedStart, LocalDateTime now) {
        return !now.isBefore(plannedStart.minus(startWindowTolerance))
                && !now.isAfter(plannedStart.plus(startWindowTolerance));
    }

    /** BR7/DEC-8: only couriers of a company registered in the platform are eligible. */
    public boolean isCourierEligible(Courier courier) {
        return courier != null && courier.getCompany() != null;
    }

    /** BR8: a deposit is required when the configured amount is positive. */
    public boolean isDepositRequired() {
        return depositAmount != null && depositAmount.signum() > 0;
    }

    /** Validates the start/end of a reservation (order, maximum duration). */
    public void validateReservationWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new BusinessRuleException("Reservation start and end times must not be null");
        }
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("Reservation end time must be after start time");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(maxReservationDuration) > 0) {
            throw new BusinessRuleException("Reservation duration exceeds maximum allowed of " + maxReservationDuration);
        }
    }

    /** DEC-4: a courier can have at most {@code maxActiveReservationsPerCourier} active reservations or sessions. */
    public boolean canCourierReserve(Long courierId, long activeReservations) {
        return activeReservations < maxActiveReservationsPerCourier;
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

    public Duration getStartWindowTolerance() {
        return startWindowTolerance;
    }

    public Duration getHoldDuration() {
        return holdDuration;
    }
}
