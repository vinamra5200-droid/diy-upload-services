package in.qualtechedge.qcp.templates.exception;

/**
 * Raised when a multipart upload body is well-formed but semantically invalid — missing
 * {@code file}, a file extension not in the template's enabled {@code uploadFormats}, or a
 * {@code templateId} that isn't the process's current active template (upload-api-contract.md
 * §2.1, error code {@code QT-VAL-422}).
 */
public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String message) {
        super(message);
    }
}
