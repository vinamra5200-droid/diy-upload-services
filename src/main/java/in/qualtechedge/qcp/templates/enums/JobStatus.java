package in.qualtechedge.qcp.templates.enums;

/** Lifecycle of one {@code upload_jobs} row (upload-api-contract.md §5). */
public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}
