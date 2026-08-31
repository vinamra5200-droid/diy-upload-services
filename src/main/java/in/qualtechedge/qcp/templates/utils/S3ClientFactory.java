package in.qualtechedge.qcp.templates.utils;

import in.qualtechedge.qcp.templates.entity.StorageConfig;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Builds an {@link S3Client} from a {@link StorageConfig} row — shared by every S3 writer
 * ({@code UploadS3Worker}, {@code ValidatedResultS3Exporter}) so the on-prem/MinIO endpoint-override
 * quirk below lives in exactly one place.
 */
public final class S3ClientFactory {

    private S3ClientFactory() {
    }

    /**
     * A custom {@code hostname}/{@code port} (S3-compatible / on-prem stores such as MinIO, or a
     * VPC endpoint) overrides the default AWS endpoint and switches to path-style addressing —
     * virtual-hosted-style needs DNS wildcard support these hosts typically don't have. TLS is
     * inferred from the port (443 → https, otherwise http); there's no separate "use TLS" field
     * on {@link StorageConfig} today, so a non-443 custom port is assumed to be a local/test store.
     */
    public static S3Client build(StorageConfig config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey());
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getBucketRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (config.getHostname() != null && !config.getHostname().isBlank()) {
            int port = config.getPort() != null ? config.getPort() : 443;
            String scheme = port == 443 ? "https" : "http";
            builder.endpointOverride(URI.create(scheme + "://" + config.getHostname() + ":" + port));
            builder.forcePathStyle(true);
        }
        return builder.build();
    }

    /** Larger than the SDK's 8MB default so a lakhs-of-rows file needs far fewer parts — each part
     * is a new HTTP connection (and therefore a new DNS lookup), and on a flaky link every one of
     * those is a fresh chance to land on a bad resolution. */
    private static final long MULTIPART_MINIMUM_PART_SIZE_BYTES = 32L * 1024 * 1024;

    /** Caps how many parts upload concurrently, so a large file's connections open in a small
     * trickle instead of all at once — fewer simultaneous fresh DNS lookups/handshakes competing
     * for whatever's causing the intermittent resolution failures. */
    private static final int MULTIPART_MAX_IN_FLIGHT_PARTS = 3;

    /**
     * Same config/endpoint-override handling as {@link #build}, but multipart-enabled and async —
     * for {@link software.amazon.awssdk.transfer.s3.S3TransferManager} to split a large upload
     * into parts and PUT them in parallel instead of one single-stream {@code PutObject}. Caller
     * owns the returned client's lifecycle (it isn't closed by handing it to a transfer manager
     * built with {@code .s3Client(...)}).
     */
    public static S3AsyncClient buildAsync(StorageConfig config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey());
        S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .region(Region.of(config.getBucketRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .multipartEnabled(true)
                .multipartConfiguration(m -> m
                        .minimumPartSizeInBytes(MULTIPART_MINIMUM_PART_SIZE_BYTES)
                        .parallelConfiguration(p -> p.maxInFlightParts(MULTIPART_MAX_IN_FLIGHT_PARTS)));

        if (config.getHostname() != null && !config.getHostname().isBlank()) {
            int port = config.getPort() != null ? config.getPort() : 443;
            String scheme = port == 443 ? "https" : "http";
            builder.endpointOverride(URI.create(scheme + "://" + config.getHostname() + ":" + port));
            builder.forcePathStyle(true);
        }
        return builder.build();
    }

    /**
     * Mints a short-lived presigned GET URL for {@code key} in {@code config}'s bucket
     * (upload-api-contract.md §2.7/§3.3/§4.2a). Shares the same endpoint-override/path-style
     * handling as {@link #build}, since a presigned URL against an on-prem/MinIO store still needs
     * to point at the overridden host.
     */
    public static PresignedGetObjectRequest presign(StorageConfig config, String key, Duration expiry) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey());
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(config.getBucketRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (config.getHostname() != null && !config.getHostname().isBlank()) {
            int port = config.getPort() != null ? config.getPort() : 443;
            String scheme = port == 443 ? "https" : "http";
            builder.endpointOverride(URI.create(scheme + "://" + config.getHostname() + ":" + port));
            builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        try (S3Presigner presigner = builder.build()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(getObjectRequest)
                    .build();
            return presigner.presignGetObject(presignRequest);
        }
    }
}
