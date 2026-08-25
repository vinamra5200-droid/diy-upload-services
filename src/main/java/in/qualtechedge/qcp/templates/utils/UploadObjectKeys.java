package in.qualtechedge.qcp.templates.utils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The interim-storage object-key template for the upload-attempt flow: {@code
 * diy-upload/{env}/{processName}/{templateName}/{stage}/{attemptId}/{filename}}. {@code
 * attemptId} ({@link in.qualtechedge.qcp.templates.entity.UploadAttempt#getUploadAttemptId()})
 * is what keeps two attempts against the same process/template that happen to share an original
 * filename from landing on the same key at any stage — without it, a later attempt's raw PUT (or
 * a later {@code CopyObject} to the same {@code pending_approval}/{@code pending_processing} key)
 * silently overwrites an earlier attempt's file, so a checker or downstream job can end up
 * downloading someone else's (or an earlier, possibly near-empty) upload instead of the one that
 * was actually promoted. {@code env} comes from {@link DeploymentEnvironment#current()}.
 * {@code processName}/{@code templateName} are free-text (user-entered), so they're normalized
 * into a lowercase, hyphenated slug before being used as a key segment.
 */
public final class UploadObjectKeys {

    private static final String TEMPLATE = "diy-upload/%s/%s/%s/%s/%s/%s";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+|-+$)");

    private UploadObjectKeys() {
    }

    public static String raw(String env, String processName, String templateName, String attemptId, String filename) {
        return key(env, processName, templateName, "raw", attemptId, filename);
    }

    public static String validated(String env, String processName, String templateName, String attemptId, String filename) {
        return key(env, processName, templateName, "validated", attemptId, filename);
    }

    public static String pendingApproval(String env, String processName, String templateName, String attemptId, String filename) {
        return key(env, processName, templateName, "pending_approval", attemptId, filename);
    }

    public static String pendingProcessing(String env, String processName, String templateName, String attemptId, String filename) {
        return key(env, processName, templateName, "pending_processing", attemptId, filename);
    }

    private static String key(String env, String processName, String templateName, String stage, String attemptId, String filename) {
        return TEMPLATE.formatted(env, normalize(processName), normalize(templateName), stage, attemptId, filename);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "unknown";
        }
        String slug = EDGE_HYPHENS.matcher(
                NON_ALPHANUMERIC.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("-")
        ).replaceAll("");
        return slug.isEmpty() ? "unknown" : slug;
    }
}
