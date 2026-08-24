package in.qualtechedge.qcp.templates.dto.response;

import java.time.OffsetDateTime;

/** §2.7/§3.3/§4.2a presigned-download response shape. */
public record PresignedDownloadResponse(
        String downloadUrl,
        OffsetDateTime expiresAt
) {
}
