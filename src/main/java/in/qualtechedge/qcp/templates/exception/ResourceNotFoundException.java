package in.qualtechedge.qcp.templates.exception;

/**
 * Thrown when a requested resource does not exist. Handled by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
