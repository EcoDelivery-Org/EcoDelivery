package pt.ua.tqs.ecodelivery.exception;

/**
 * Thrown when the security deposit is refused (BR8). It is a business-rule violation, but the
 * transaction is not rolled back for it, so the failed payment stays recorded.
 */
public class DepositFailedException extends BusinessRuleException {

    public DepositFailedException(String message) {
        super(message);
    }
}
