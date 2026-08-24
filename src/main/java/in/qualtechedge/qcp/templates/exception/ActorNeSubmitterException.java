package in.qualtechedge.qcp.templates.exception;

/**
 * Raised when a checker's actorId equals the submission's makerUserId — the four-eyes rule
 * (upload-api-contract.md §4.3/§4.4/§4.2a, error code {@code QT-BIZ-403}).
 */
public class ActorNeSubmitterException extends RuntimeException {

    public ActorNeSubmitterException(String message) {
        super(message);
    }
}
