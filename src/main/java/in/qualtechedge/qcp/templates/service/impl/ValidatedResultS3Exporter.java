package in.qualtechedge.qcp.templates.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import in.qualtechedge.qcp.templates.entity.BatchUploadResultRow;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRepository;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRowRepository;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.utils.DeploymentEnvironment;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Builds a row-by-row CSV of one batch's validation results (every row, pass or fail) and writes
 * it to S3 under {@code diy-upload/{env}/{processId}/{templateId}/validated/} — the reverse-side
 * counterpart of {@link UploadS3Worker}'s raw-file PUT. Kicked off from
 * {@link in.qualtechedge.qcp.templates.consumer.BatchValidationCompletedListener} after row
 * results are committed, and runs off that Kafka consumer thread so a large batch's export never
 * delays the completion event's acknowledgment — row-by-row results are already visible to the UI
 * regardless of whether this export succeeds; a failure here is logged only, never retried.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidatedResultS3Exporter {

    private static final String KEY_TEMPLATE = "diy-upload/%s/%s/%s/validated/%s";
    private static final TypeReference<List<Map<String, Object>>> ERRORS_TYPE = new TypeReference<>() { };

    private final BatchUploadResultRepository batchUploadResultRepository;
    private final BatchUploadResultRowRepository batchUploadResultRowRepository;
    private final UploadFileRepository uploadFileRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final DeploymentEnvironment deploymentEnvironment;
    private final PlatformTransactionManager transactionManager;

    @Async("uploadTaskExecutor")
    public void export(String tenant, UUID batchId) {
        // Same reasoning as UploadS3Worker#process — this runs on a fresh thread pool thread,
        // which doesn't inherit the Kafka consumer thread's HostContext ThreadLocal.
        HostContext.setCurrentTenant(tenant);
        try {
            BatchUploadResult result = batchUploadResultRepository.findById(batchId).orElse(null);
            UploadFile uploadFile = uploadFileRepository.findFirstByJobId(batchId.toString()).orElse(null);
            if (result == null || uploadFile == null) {
                log.warn("Skipping validated-result export — result or upload file not found: batchId={}", batchId);
                return;
            }

            Path tempFile = tempFilePath(batchId);
            try {
                long rowCount = writeCsv(batchId, tempFile);
                String filename = baseName(uploadFile.getOriginalFilename()) + "_validated.csv";
                putToS3(result, uploadFile, filename, tempFile);
                batchUploadResultRepository.save(result);
                log.info("Validated result exported to S3: batchId={}, bucket={}, key={}, rowCount={}",
                        batchId, result.getResultS3Bucket(), result.getResultS3Key(), rowCount);
            } catch (RuntimeException e) {
                log.error("Validated result export failed: batchId={}", batchId, e);
            } finally {
                deleteQuietly(tempFile);
            }
        } finally {
            HostContext.clear();
        }
    }

    /**
     * Runs in its own read-only transaction so the row {@link Stream}'s underlying cursor stays
     * open while rows are written to disk one at a time — a lakh-row batch never sits fully in
     * JVM heap (same bounded-memory rule {@code UploadFileRowReader} applies to raw uploads).
     * {@link TransactionTemplate}, not {@code @Transactional}, because this is called from within
     * {@link #export}'s own async method on the same bean — an annotation would be silently
     * ignored by the proxy on that self-invocation.
     */
    private long writeCsv(UUID batchId, Path tempFile) {
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);
        Long rowCount = readOnlyTx.execute(status -> {
            CsvSchema schema = CsvSchema.builder()
                    .addColumn("row_number")
                    .addColumn("row_status")
                    .addColumn("row_data")
                    .addColumn("errors")
                    .setUseHeader(true)
                    .build();
            try (OutputStream out = Files.newOutputStream(tempFile);
                    Stream<BatchUploadResultRow> rows = batchUploadResultRowRepository.streamByBatchIdOrderByRowNumberAsc(batchId)) {
                SequenceWriter writer = new CsvMapper().writer(schema).writeValues(out);
                long[] count = {0};
                rows.forEach(row -> writeRow(writer, row, count));
                writer.close();
                return count[0];
            } catch (IOException | UncheckedIOException e) {
                throw new IllegalStateException("Failed to write validated-result CSV", e);
            }
        });
        return rowCount == null ? 0 : rowCount;
    }

    private void writeRow(SequenceWriter writer, BatchUploadResultRow row, long[] count) {
        try {
            writer.write(new String[] {
                    String.valueOf(row.getRowNumber()),
                    row.getRowStatus(),
                    row.getRowData(),
                    formatErrors(row.getErrors())
            });
            count[0]++;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code field: message (SEVERITY)} pairs, joined for spreadsheet readability — friendlier than raw JSON in a CSV cell. */
    private String formatErrors(String errorsJson) {
        List<Map<String, Object>> errors = JsonColumnMapper.read(errorsJson, ERRORS_TYPE);
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .map(e -> e.getOrDefault("field", "") + ": " + e.getOrDefault("errorMessage", "")
                        + " (" + e.getOrDefault("severity", "") + ")")
                .collect(Collectors.joining(" | "));
    }

    private void putToS3(BatchUploadResult result, UploadFile uploadFile, String filename, Path tempFile) {
        StorageConfig config = storageConfigRepository
                .findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        assertS3FieldsPresent(config);

        String key = KEY_TEMPLATE.formatted(deploymentEnvironment.current(), uploadFile.getProcessId(),
                uploadFile.getTemplateId(), filename);

        try (S3Client client = S3ClientFactory.build(config)) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType("text/csv")
                    .build();
            client.putObject(request, RequestBody.fromFile(tempFile));
            result.setResultS3Bucket(config.getBucketName());
            result.setResultS3Key(key);
        } catch (S3Exception e) {
            log.error("Validated-result S3 upload failed: bucket={}, key={}, status={}", config.getBucketName(), key, e.statusCode(), e);
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new IllegalStateException("S3 upload failed: " + detail, e);
        }
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

    private String baseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "result";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private Path tempFilePath(UUID batchId) {
        return Path.of(System.getProperty("java.io.tmpdir"), "diy-upload-validated-" + batchId + ".tmp");
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp validated-result file: {}", path, e);
        }
    }
}
