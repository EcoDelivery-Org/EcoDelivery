package pt.ua.tqs.ecodelivery.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps exceptions to HTTP problem-detail responses for every controller. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 Not Found */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** 409 Conflict */
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
