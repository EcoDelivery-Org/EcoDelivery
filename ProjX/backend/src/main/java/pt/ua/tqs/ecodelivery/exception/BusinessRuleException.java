package pt.ua.tqs.ecodelivery.exception;

/** Thrown when an operation breaks a business rule (e.g. booking an unavailable bike). Mapped to 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
