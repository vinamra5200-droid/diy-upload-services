package in.qualtechedge.qcp.templates.enums;

/** Lifecycle of one {@code upload_submissions} row (upload-api-contract.md §5). */
public enum SubmissionStatus {
    WAITING_FOR_CHECKER,
    ACCEPTED,
    REJECTED,
    EXPIRED
}
