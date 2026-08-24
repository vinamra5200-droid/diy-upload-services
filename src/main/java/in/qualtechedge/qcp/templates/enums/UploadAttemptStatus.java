package in.qualtechedge.qcp.templates.enums;

/** Lifecycle of one {@code upload_attempts} row (upload-api-contract.md §5). */
public enum UploadAttemptStatus {
    ACCEPTED,
    VALIDATING,
    READY_FOR_DECISION,
    CONTINUED,
    REUPLOADED,
    TIMED_OUT,
    ABORTED
}
