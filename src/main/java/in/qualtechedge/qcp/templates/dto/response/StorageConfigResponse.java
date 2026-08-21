package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import java.time.OffsetDateTime;

/** {@code secretAccessKey} is always masked (see {@code SecretMasking}) — never the live value. */
public record StorageConfigResponse(
        String configId,
        InterimStoreProvider provider,
        String connectionLabel,
        String connectionRef,
        String bucketName,
        String bucketRegion,
        String accessKeyId,
        String secretAccessKey,
        String hostname,
        Integer port,
        String pathPattern,
        ConfigStatus status,
        String submittedBy,
        String rejectionReason,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
