package in.qualtechedge.qcp.templates.exception;

/**
 * Raised when a checker acts on an {@code UploadSubmission} past its {@code expiresAt} — treated
 * as already {@code EXPIRED} (upload-api-contract.md §4.3/§4.4, error code {@code QT-BIZ-410}).
 */
public class SubmissionExpiredException extends RuntimeException {

    public SubmissionExpiredException(String message) {
        super(message);
    }
}
