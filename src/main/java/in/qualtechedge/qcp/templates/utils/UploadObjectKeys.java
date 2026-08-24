package in.qualtechedge.qcp.templates.utils;

/**
 * The interim-storage object-key template for the upload-attempt flow (upload-api-contract.md
 * §6): {@code diy-upload/{templateId}/{processId}/{stage}/{filename}}. Deliberately different
 * from the older raw-upload flow's {@code UploadS3Worker} key template (which includes an
 * {@code {env}} segment and reverses the process/template order) — that flow writes to a separate
 * table (`upload_files`) and is unaffected by this one.
 */
public final class UploadObjectKeys {

    private static final String TEMPLATE = "diy-upload/%s/%s/%s/%s";

    private UploadObjectKeys() {
    }

    public static String raw(String templateId, String processId, String filename) {
        return key(templateId, processId, "raw", filename);
    }

    public static String validated(String templateId, String processId, String filename) {
        return key(templateId, processId, "validated", filename);
    }

    public static String pendingApproval(String templateId, String processId, String filename) {
        return key(templateId, processId, "pending_approval", filename);
    }

    public static String pendingProcessing(String templateId, String processId, String filename) {
        return key(templateId, processId, "pending_processing", filename);
    }

    private static String key(String templateId, String processId, String stage, String filename) {
        return TEMPLATE.formatted(templateId, processId, stage, filename);
    }
}
