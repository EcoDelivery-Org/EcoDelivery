package pt.ua.tqs.ecodelivery.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.model.Courier;

/**
 * Plain unit test: no Spring, no mocks. {@link PolicyService} only holds the rules of use
 * configured by the institutional client, so it is built directly with known values.
 */
@DisplayName("PolicyService — rules of use for US 10 (BR1, BR4/DEC-5, BR7/DEC-8, BR8, DEC-1, DEC-4)")
class PolicyServiceTest {

    private static final LocalDateTime PLANNED_START = LocalDateTime.of(2026, 11, 23, 10, 0);
    private static final Duration MAX_DURATION = Duration.ofHours(6);

    private static PolicyService policyWithDeposit(BigDecimal deposit) {
        return new PolicyService(20, MAX_DURATION, deposit, new BigDecimal("0.10"), 1, Duration.ofMinutes(15));
    }

    private final PolicyService policy = policyWithDeposit(new BigDecimal("10.00"));

    @ParameterizedTest(name = "battery {0} % -> sufficient: {1}")
    @CsvSource({
            "0,   false",
            "19,  false",  // BR1, boundary: just below the minimum
            "20,  true",   // BR1, boundary: exactly the minimum is accepted
            "21,  true",
            "100, true"
    })
    @DisplayName("BR1: the battery must be at or above the minimum level (20 %)")
    void batteryMustBeAtOrAboveTheMinimum(int batteryLevel, boolean expected) {
        assertEquals(expected, policy.isBatterySufficient(batteryLevel), "battery " + batteryLevel + " %");
    }

    @ParameterizedTest(name = "start {0} min from the planned start -> allowed: {1}")
    @CsvSource({
            "-16, false",  // DEC-5, too early
            "-15, true",   // DEC-5, boundary: exactly 15 min before is allowed
            "0,   true",
            "15,  true",   // DEC-5, boundary: exactly 15 min after is allowed
            "16,  false"   // DEC-5, too late
    })
    @DisplayName("BR4/DEC-5: a reservation can be started from 15 min before to 15 min after its planned start")
    void reservationCanOnlyBeStartedInsideItsWindow(long minutesFromPlannedStart, boolean expected) {
        LocalDateTime now = PLANNED_START.plusMinutes(minutesFromPlannedStart);

        assertEquals(expected, policy.isWithinStartWindow(PLANNED_START, now),
                minutesFromPlannedStart + " min from the planned start");
    }

    @Test
    @DisplayName("BR7/DEC-8: a courier of a registered company is eligible")
    void courierOfARegisteredCompanyIsEligible() {
        Company company = new Company();
        company.setName("Rapid Parcels");
        Courier courier = new Courier();
        courier.setCompany(company);

        assertTrue(policy.isCourierEligible(courier));
    }

    @Test
    @DisplayName("BR7/DEC-8: a courier without a registered company is not eligible")
    void courierWithoutCompanyIsNotEligible() {
        Courier courier = new Courier();

        assertFalse(policy.isCourierEligible(courier));
    }

    @Test
    @DisplayName("BR8: a deposit is required when the configured amount is positive")
    void depositIsRequiredWhenTheAmountIsPositive() {
        assertTrue(policyWithDeposit(new BigDecimal("10.00")).isDepositRequired());
    }

    @Test
    @DisplayName("BR8: no deposit is required when the configured amount is zero")
    void noDepositIsRequiredWhenTheAmountIsZero() {
        assertFalse(policyWithDeposit(BigDecimal.ZERO).isDepositRequired());
    }

    @ParameterizedTest(name = "{0} active -> can take another: {1}")
    @CsvSource({
            "0, true",
            "1, false"   // DEC-4, boundary: the limit (1) is already reached
    })
    @DisplayName("DEC-4: a courier can have at most one active reservation or session")
    void courierCanHaveAtMostOneActiveReservation(long activeReservations, boolean expected) {
        assertEquals(expected, policy.canCourierReserve(10L, activeReservations));
    }

    @Test
    @DisplayName("DEC-1: the maximum duration is the value configured by the client")
    void maximumDurationIsTheConfiguredValue() {
        assertEquals(MAX_DURATION, policy.getMaxReservationDuration());
    }
}
