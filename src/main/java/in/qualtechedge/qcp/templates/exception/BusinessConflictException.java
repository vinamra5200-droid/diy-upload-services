package in.qualtechedge.qcp.templates.exception;

/**
 * Raised for the upload-operator module's own business-rule conflicts (upload-api-contract.md
 * "Module Error Codes" — {@code QT-BIZ-409}): a concurrent upload attempt already in flight for a
 * process, or an action that doesn't match the resource's current status (e.g. proceed on a
 * non-{@code READY_FOR_DECISION} attempt). Deliberately distinct from {@link ConflictException}
 * ({@code QT-RES-409}), which is the admin/config module's own 409 — kept separate so this
 * addition doesn't change the admin module's already-shipped error code.
 */
public class BusinessConflictException extends RuntimeException {

    public BusinessConflictException(String message) {
        super(message);
    }
}
