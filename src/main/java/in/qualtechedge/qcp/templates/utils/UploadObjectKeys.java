package in.qualtechedge.qcp.templates.utils;

/**
 * The interim-storage object-key template for the upload-attempt flow: {@code
 * diy-upload/{env}/{tenantCode}/{processId}/{templateId}/{stage}/{uniqueId}/{filename}}. Every
 * stage (raw, validated, dispatch, processed) sits under the same {@code {processId}/{templateId}}
 * segment — process/template *ids*, not free-text names: ids are stable and already unique per
 * tenant database, so no slugification/normalization step is needed, and a maker who renames a
 * process mid-flight can never split one attempt's objects across two different folders. {@code
 * tenantCode} keeps one tenant's objects from ever colliding with another's — process and template
 * ids/sequences are per-tenant-database, so {@code proc-000001}/{@code tmpl-000001} exists
 * independently in every tenant, and without this segment two tenants' uploads against "the same"
 * process/template/filename would land on the exact same key in a shared bucket. {@code uniqueId}
 * (an attempt id, batch id, or job id depending on the stage) does the same job within one tenant:
 * two attempts/batches/jobs that happen to share an original filename never land on the same key at
 * any stage — without it, a later write silently overwrites an earlier one, so a checker or
 * downstream job can end up reading someone else's (or an earlier, possibly stale) file instead of
 * the one that was actually meant. {@code env} comes from {@link DeploymentEnvironment#current()}.
 */
public final class UploadObjectKeys {

    private static final String TEMPLATE = "diy-upload/%s/%s/%s/%s/%s/%s/%s";

    private UploadObjectKeys() {
    }

    /** {@code stage=raw}, {@code uniqueId}=the attempt's own id — written once, at upload time,
     * straight from the maker's file. */
    public static String raw(String env, String tenantCode, String processId, String templateId, String attemptId, String filename) {
        return key(env, tenantCode, processId, templateId, "raw", attemptId, filename);
    }

    /**
     * {@code stage=validated}, {@code uniqueId}=batchId for a real validation run
     * ({@code ValidatedResultS3Exporter}) or attemptId for a validation-skipped promotion
     * ({@code UploadAttemptServiceImpl#promoteToValidated}) — previously two different,
     * independently-drifting key shapes existed for the same stage depending on which path
     * produced the object; both now write through this one. Every row (pass or fail) plus
     * row_status/errors, for the maker's own review/download — never read for dispatch.
     */
    public static String validated(String env, String tenantCode, String processId, String templateId, String uniqueId, String filename) {
        return key(env, tenantCode, processId, templateId, "validated", uniqueId, filename);
    }

    /**
     * {@code stage=dispatch}, {@code uniqueId}=the owning attempt's id — a clean, passed-rows-only
     * rebuild of the sheet, written by {@code PassedRowsFileBuilder} right before a job is created
     * (direct or checker-approved) and never read for anything but that job's own
     * {@code completedFileKey}/{@code originalObjectKey}. Distinct from {@link #validated}, which
     * stays every row (pass or fail): the two must never collapse into one key shape, or a failed
     * row ends up dispatched to the third party again.
     */
    public static String dispatch(String env, String tenantCode, String processId, String templateId, String uniqueId, String filename) {
        return key(env, tenantCode, processId, templateId, "dispatch", uniqueId, filename);
    }

    /**
     * {@code stage=processed}, {@code uniqueId}=jobId — written by {@code ProcessedResultS3Exporter}
     * once consumer-callback-service finishes delivering every batch of a job: the dispatched sheet's
     * own columns plus {@code status}/{@code api_response} appended per row.
     */
    public static String processed(String env, String tenantCode, String processId, String templateId, String jobId, String filename) {
        return key(env, tenantCode, processId, templateId, "processed", jobId, filename);
    }

    private static String key(String env, String tenantCode, String processId, String templateId, String stage, String uniqueId, String filename) {
        return TEMPLATE.formatted(env, tenantCode, processId, templateId, stage, uniqueId, filename);
    }
}
