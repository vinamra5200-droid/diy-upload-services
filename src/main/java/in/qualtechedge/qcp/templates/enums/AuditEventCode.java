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
 * {@code PIPELINE} values are the Solution Design §12.3 catalogue. {@link #FILE_RECEIVED},
 * {@link #FILE_REJECTED}, {@link #S3_WRITE_COMPLETED} (S3UploadServiceImpl / UploadS3Worker), and
 * {@link #ENQUEUE_PUSHED}/{@link #ENQUEUE_FAILED} ({@code BatchChunkPublisherImpl}, once
 * {@code UploadS3Worker} hands it the file) are emitted today — the rest are catalogued ahead of
 * the backend layer (validation, checker review, S3 promote) that will eventually emit them; see
 * V1_0_52..54's "referential completeness only" comments. {@link #S3_WRITE_COMPLETED} is emitted
 * against the lightweight {@code upload_files.job_id} (V1_0_61) — deliberately not
 * {@link #JOB_METADATA_CREATED}, which per SD §12.3 fires later, after checker approval and S3
 * promote (#20-25); this flow has no maker-checker gate yet, so completing the raw S3 PUT here is
 * accurately the #25 dual-control-off write, not the downstream #26 job. {@link #ENQUEUE_PUSHED}/
 * {@link #ENQUEUE_FAILED} (#27-28) report on the Kafka publish that same {@code job_id} keys, one
 * event per upload regardless of how many chunks it took.
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
    S3_WRITE_COMPLETED,         // #25 — emitted: UploadS3Worker#recordS3WriteCompleted
    JOB_METADATA_CREATED,       // #26
    ENQUEUE_PUSHED,             // #27 — emitted: BatchChunkPublisherImpl#recordEnqueuePushed
    ENQUEUE_FAILED,             // #28 — emitted: BatchChunkPublisherImpl#recordEnqueueFailed
    SESSION_FINALIZED           // #29
}
