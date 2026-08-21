package in.qualtechedge.qcp.templates.enums;

/**
 * Every audit_events.event_code value this system is allowed to record — the Java-side mirror of
 * the {@code audit_event_catalogue} static table (V1_0_59), which is the actual source of truth
 * (a foreign key on {@code audit_events.event_code} enforces it at the DB level too).
 * <p>
 * {@code ADMIN_*} values are recorded today by every resource's maker-checker service methods
 * (admin-api-contract.md §9) via the pre-existing {@code AuditEventService#record(String, ...)}
 * overload — they're listed here for completeness but those call sites weren't migrated to this
 * enum, to avoid touching 30+ already-working call sites for no functional change.
 * <p>
 * {@code PIPELINE} values are the Solution Design §12.3 catalogue. Only {@link #FILE_RECEIVED},
 * {@link #FILE_REJECTED}, and {@link #JOB_METADATA_CREATED} are emitted anywhere today
 * (S3UploadServiceImpl / UploadS3Worker) — the rest are catalogued ahead of the backend layer
 * (validation, checker review, queue push) that will eventually emit them; see V1_0_52..54's
 * "referential completeness only" comments. Note {@link #JOB_METADATA_CREATED} here is emitted
 * against the lightweight {@code upload_files.job_id} (V1_0_61), not the {@code upload_jobs} row
 * from those referential-only tables — that table still needs the validation/checker/promote
 * layer built before anything can populate it for real.
 */
public enum AuditEventCode {

    // --- Admin config-mutation events (admin-api-contract.md §9) ---
    ADMIN_ROLE_CREATED,
    ADMIN_ROLE_UPDATED,
    ADMIN_ROLE_SUBMITTED,
    ADMIN_ROLE_ACTIVATED,
    ADMIN_ROLE_REJECTED,
    ADMIN_DATABASE_CREATED,
    ADMIN_DATABASE_UPDATED,
    ADMIN_DATABASE_SUBMITTED,
    ADMIN_DATABASE_ACTIVATED,
    ADMIN_DATABASE_REJECTED,
    ADMIN_API_CONFIG_CREATED,
    ADMIN_API_CONFIG_UPDATED,
    ADMIN_API_CONFIG_SUBMITTED,
    ADMIN_API_CONFIG_ACTIVATED,
    ADMIN_API_CONFIG_REJECTED,
    ADMIN_TEMPLATE_CREATED,
    ADMIN_TEMPLATE_UPDATED,
    ADMIN_TEMPLATE_SUBMITTED,
    ADMIN_TEMPLATE_ACTIVATED,
    ADMIN_TEMPLATE_REJECTED,
    ADMIN_TEMPLATE_CLONED,
    ADMIN_USER_CREATED,
    ADMIN_USER_UPDATED,
    ADMIN_USER_SUBMITTED,
    ADMIN_USER_ACTIVATED,
    ADMIN_USER_REJECTED,
    ADMIN_PROCESS_CREATED,
    ADMIN_PROCESS_UPDATED,
    ADMIN_PROCESS_SUBMITTED,
    ADMIN_PROCESS_ACTIVATED,
    ADMIN_PROCESS_REJECTED,
    ADMIN_STORAGE_CREATED,
    ADMIN_STORAGE_UPDATED,
    ADMIN_STORAGE_SUBMITTED,
    ADMIN_STORAGE_ACTIVATED,
    ADMIN_STORAGE_REJECTED,

    // --- Upload-pipeline events (SD §12.3, numbered per the doc) ---
    AUTH_OK,                    // #1
    PROCESS_SELECTED,           // #2
    TEMPLATE_SELECTED,          // #3
    TEMPLATE_DOWNLOADED,        // #4
    FILE_RECEIVED,              // #5  — emitted: S3UploadServiceImpl#upload
    FILE_REJECTED,              // #6  — emitted: S3UploadServiceImpl#upload
    CONCURRENT_UPLOAD_REJECTED, // #6a
    VALIDATION_STARTED,         // #8
    VALIDATION_CHUNK_DONE,      // #9
    VALIDATION_COMPLETED,       // #10
    VALIDATION_SKIPPED,         // #11
    SESSION_TIMED_OUT,          // #11a
    DECISION_REUPLOAD,          // #15
    DECISION_PROCEED,           // #16
    S3_PENDING_WRITE_STARTED,   // #17-19
    S3_PENDING_WRITE_COMPLETED, // #17-19
    S3_PENDING_WRITE_FAILED,    // #17-19
    CHECKER_SUBMITTED,          // #20
    CHECKER_APPROVED,           // #21
    CHECKER_REJECTED,           // #22
    S3_PROMOTE_COMPLETED,       // #23-24
    S3_PROMOTE_FAILED,          // #23-24
    S3_WRITE_COMPLETED,         // #25
    JOB_METADATA_CREATED,       // #26 — emitted: UploadS3Worker#recordJobMetadataCreated
    ENQUEUE_PUSHED,             // #27
    ENQUEUE_FAILED,             // #28
    SESSION_FINALIZED           // #29
}
