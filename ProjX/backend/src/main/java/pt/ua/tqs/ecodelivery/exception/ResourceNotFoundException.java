package pt.ua.tqs.ecodelivery.exception;

/** Thrown when a requested resource (bike, hub, reservation...) does not exist. Mapped to 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
