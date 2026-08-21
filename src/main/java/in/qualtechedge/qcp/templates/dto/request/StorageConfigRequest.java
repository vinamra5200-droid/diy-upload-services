package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code bucketName}/{@code bucketRegion}/{@code accessKeyId}/{@code secretAccessKey}/
 * {@code hostname}/{@code port} apply to {@code provider = AWS_S3} only. "Required when
 * provider = AWS_S3" is enforced by the frontend Zod validator, not here — the same pattern
 * already used for {@code PostLoadActionRequest}'s conditional kafka/database fields. The one
 * exception is {@code hostname}/{@code port}: those two are genuinely optional even for AWS_S3 —
 * they only override the endpoint for an S3-compatible/on-prem store or VPC endpoint (see
 * {@link in.qualtechedge.qcp.templates.service.impl.UploadS3Worker#buildClient}); leaving them
 * blank makes the AWS SDK resolve the correct regional endpoint from {@code bucketRegion} itself.
 * <p>
 * A blank {@code secretAccessKey} on an update request means "leave the stored secret
 * unchanged" (see {@link in.qualtechedge.qcp.templates.mapper.StorageConfigMapper}) — the UI
 * only ever displays a masked value and cannot resend the real one.
 */
public record StorageConfigRequest(
        @NotNull(message = "provider must not be null")
        InterimStoreProvider provider,

        @NotBlank(message = "connectionLabel must not be blank")
        @Size(max = 120, message = "connectionLabel must be at most 120 characters")
        String connectionLabel,

        @NotBlank(message = "connectionRef must not be blank")
        @Size(max = 500, message = "connectionRef must be at most 500 characters")
        String connectionRef,

        @Size(max = 255, message = "bucketName must be at most 255 characters")
        String bucketName,

        @Size(max = 64, message = "bucketRegion must be at most 64 characters")
        String bucketRegion,

        @Size(max = 128, message = "accessKeyId must be at most 128 characters")
        String accessKeyId,

        @Size(max = 256, message = "secretAccessKey must be at most 256 characters")
        String secretAccessKey,

        @Size(max = 255, message = "hostname must be at most 255 characters")
        String hostname,

        @Min(value = 1, message = "port must be between 1 and 65535")
        @Max(value = 65535, message = "port must be between 1 and 65535")
        Integer port
) {
}
