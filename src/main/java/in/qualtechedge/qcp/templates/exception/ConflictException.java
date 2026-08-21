package in.qualtechedge.qcp.templates.exception;

/** Raised when a create/update collides with an existing resource (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
