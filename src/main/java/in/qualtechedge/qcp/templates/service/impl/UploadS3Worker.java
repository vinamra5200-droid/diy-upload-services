package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.UploadFileStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadFileMapper;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.UploadEventPublisher;
import in.qualtechedge.qcp.templates.utils.DeploymentEnvironment;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Runs the actual S3 PUT off the request thread (see {@link in.qualtechedge.qcp.templates.config.AsyncConfig})
 * so a large file doesn't hold the upload POST open for as long as the transfer takes. Publishes
 * every status transition via {@link UploadEventPublisher} so the frontend can watch over SSE
 * instead of polling.
 * <p>
 * Must be a separate Spring bean from {@link S3UploadServiceImpl} — {@code @Async} is
 * proxy-based, so calling an {@code @Async} method on {@code this} from within the same class
 * silently runs synchronously instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadS3Worker {

    private static final String KEY_TEMPLATE = "diy-upload/%s/%s/%s/raw/%s";

    private final UploadFileRepository uploadFileRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final DeploymentEnvironment deploymentEnvironment;
    private final UploadEventPublisher uploadEventPublisher;
    private final UploadFileMapper uploadFileMapper;

    @Async("uploadTaskExecutor")
    public void process(String uploadId, String filename, String contentType, Path tempFile) {
        UploadFile record = uploadFileRepository.findById(uploadId).orElse(null);
        if (record == null) {
            log.warn("Upload record {} vanished before background processing started", uploadId);
            deleteQuietly(tempFile);
            return;
        }

        record.setStatus(UploadFileStatus.inProgress);
        UploadFile inProgress = uploadFileRepository.save(record);
        uploadEventPublisher.publish(uploadFileMapper.toResponse(inProgress));

        try {
            putToS3(record, filename, contentType, tempFile);
            record.setStatus(UploadFileStatus.completed);
            UploadFile completed = uploadFileRepository.save(record);
            log.info("Uploaded to S3: bucket={}, key={}, uploadId={}", completed.getS3Bucket(), completed.getS3Key(), uploadId);
            uploadEventPublisher.publish(uploadFileMapper.toResponse(completed));
        } catch (RuntimeException e) {
            log.error("Upload {} failed", uploadId, e);
            record.setStatus(UploadFileStatus.failed);
            record.setErrorMessage(e.getMessage());
            UploadFile failed = uploadFileRepository.save(record);
            uploadEventPublisher.publish(uploadFileMapper.toResponse(failed));
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private void putToS3(UploadFile record, String filename, String contentType, Path tempFile) {
        StorageConfig config = storageConfigRepository
                .findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        assertS3FieldsPresent(config);

        String key = KEY_TEMPLATE.formatted(deploymentEnvironment.current(), record.getProcessId(), record.getTemplateId(), filename);

        try (S3Client client = buildClient(config)) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();
            // RequestBody.fromFile streams from disk (content-length is derived from the file) —
            // the file never has to fit in JVM heap, which matters since a maker's upload can run
            // to lakhs of rows.
            PutObjectResponse response = client.putObject(request, RequestBody.fromFile(tempFile));
            record.setS3Bucket(config.getBucketName());
            record.setS3Key(key);
            record.setEtag(response.eTag());
        } catch (S3Exception e) {
            log.error("S3 upload failed: bucket={}, key={}, status={}", config.getBucketName(), key, e.statusCode(), e);
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new IllegalStateException("S3 upload failed: " + detail, e);
        }
    }

    /**
     * A custom {@code hostname}/{@code port} (S3-compatible / on-prem stores such as MinIO, or a
     * VPC endpoint) overrides the default AWS endpoint and switches to path-style addressing —
     * virtual-hosted-style needs DNS wildcard support these hosts typically don't have. TLS is
     * inferred from the port (443 → https, otherwise http); there's no separate "use TLS" field
     * on {@link StorageConfig} today, so a non-443 custom port is assumed to be a local/test store.
     */
    private S3Client buildClient(StorageConfig config) {
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

    private void assertS3FieldsPresent(StorageConfig config) {
        if (isBlank(config.getBucketName()) || isBlank(config.getBucketRegion())
                || isBlank(config.getAccessKeyId()) || isBlank(config.getSecretAccessKey())) {
            throw new ConflictException("The active AWS_S3 storage connection is missing bucket/credential fields");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp upload file: {}", path, e);
        }
    }
}
