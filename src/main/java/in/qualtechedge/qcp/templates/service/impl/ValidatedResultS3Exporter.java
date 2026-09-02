package in.qualtechedge.qcp.templates.service.impl;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRepository;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import in.qualtechedge.qcp.templates.utils.DeploymentEnvironment;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import in.qualtechedge.qcp.templates.utils.UploadObjectKeys;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

/**
 * Builds a row-by-row CSV of one batch's validation results (every row, pass or fail) and writes
 * it to S3 via {@link UploadObjectKeys#validated} — the reverse-side counterpart of
 * {@link UploadS3Worker}'s raw-file PUT, and the same key shape
 * {@code UploadAttemptServiceImpl#promoteToValidated} uses for the validation-skipped path, so the
 * "validated" stage always sits at one predictable shape regardless of which path produced it.
 * Kicked off from
 * {@link in.qualtechedge.qcp.templates.controller.BatchUploadController} after the completion
 * callback is recorded, and runs off that request thread (not the Tomcat thread handling the
 * callback) so a large batch's export never delays the HTTP response — the maker's interactive
 * results browsing (paginated, on-demand) doesn't wait on this either, since it goes straight to
 * validation-service itself rather than any local copy this export builds. A failure here is
 * logged only, never retried.
 * <p>
 * Streams every row straight from validation-service ({@link ValidationServiceResultsClient#streamRows})
 * rather than reading a local pre-populated copy — there is no local copy anymore; keeping one
 * just for this one occasional export would mean re-introducing the eager pull-everything-on-
 * completion this class's sibling change (recordCompletion no longer calling streamRows) removed.
 * <p>
 * The CSV mirrors the maker's original sheet column-for-column ({@link CsvRowWriter} derives the
 * header from the first row's {@code rowData} keys, which are that row's original column names in
 * their original order) rather than dumping each row as one JSON blob — the point of this export
 * is a file the maker can open and read against the source data, not a re-encoded API response.
 * {@code row_status} and {@code errors} are appended after the sheet's own columns; a row with
 * failures across several fields gets every {@code field: reason} pair for that row, joined into
 * one cell, so nothing is silently dropped when a row fails more than one rule.
 * <p>
 * Writes the key to two places once the upload succeeds: {@code BatchUploadResult.resultS3Key}
 * (the admin-facing summary) and, when this batch came through the maker-upload-attempt flow,
 * {@code UploadAttempt.validatedObjectKey} too — that field is what {@code stage=validated}
 * downloads actually presign, so this is the one place that makes that stage resolve to the real
 * transformed file instead of a stale/absent key. Owner lookup tries {@link UploadAttempt} (keyed
 * by {@code batchId}, the current flow) before falling back to the older {@link UploadFile} (keyed
 * by {@code jobId}) — see {@link #resolveOwner}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidatedResultS3Exporter {

    private final BatchUploadResultRepository batchUploadResultRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final UploadFileRepository uploadFileRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final DeploymentEnvironment deploymentEnvironment;
    private final ValidationServiceResultsClient validationServiceResultsClient;

    @Async("uploadTaskExecutor")
    public void export(String tenant, UUID batchId) {
        // Same reasoning as UploadS3Worker#process — this runs on a fresh thread pool thread,
        // which doesn't inherit the calling request thread's HostContext ThreadLocal.
        HostContext.setCurrentTenant(tenant);
        try {
            BatchUploadResult result = batchUploadResultRepository.findById(batchId).orElse(null);
            Optional<UploadAttempt> attempt = uploadAttemptRepository.findByBatchId(batchId);
            ExportOwner owner = resolveOwner(batchId, attempt);
            if (result == null || owner == null) {
                log.warn("Skipping validated-result export — result or upload owner not found: batchId={}", batchId);
                return;
            }

            Path tempFile = tempFilePath(batchId);
            try {
                long rowCount = writeCsv(tenant, batchId, tempFile);
                String filename = baseName(owner.originalFilename()) + "_validated.csv";
                String key = putToS3(result, owner, batchId, filename, tempFile);
                batchUploadResultRepository.save(result);
                attempt.ifPresent(a -> {
                    a.setValidatedObjectKey(key);
                    uploadAttemptRepository.save(a);
                });
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
     * {@link UploadAttempt} (the current maker-upload-attempt flow, keyed by {@code batchId}) is
     * tried first; {@link UploadFile} (the older {@code upload_files} flow, keyed by {@code jobId})
     * is a fallback for whatever still goes through that path. Both carry the same three fields
     * this export needs — original filename plus the process/template ids the S3 key is built
     * from — under different names, hence this small common shape.
     */
    private ExportOwner resolveOwner(UUID batchId, Optional<UploadAttempt> attempt) {
        return attempt.<ExportOwner>map(a -> new ExportOwner(a.getOriginalFilename(), a.getProcessId(), a.getTemplateId()))
                .or(() -> uploadFileRepository.findFirstByJobId(batchId.toString())
                        .map(f -> new ExportOwner(f.getOriginalFilename(), f.getProcessId(), f.getTemplateId())))
                .orElse(null);
    }

    private record ExportOwner(String originalFilename, String processId, String templateId) {
    }

    private long writeCsv(String tenant, UUID batchId, Path tempFile) {
        try (OutputStream out = Files.newOutputStream(tempFile)) {
            CsvRowWriter rowWriter = new CsvRowWriter(out);
            validationServiceResultsClient.streamRows(batchId, tenant, page -> page.forEach(rowWriter::write));
            rowWriter.close();
            return rowWriter.count;
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("Failed to write validated-result CSV", e);
        }
    }

    /**
     * Header is fixed on the first row seen — {@code row_number}, then that row's {@code rowData}
     * keys in their original order, then {@code row_status} and {@code errors} — and every later
     * row is written against those same columns. Safe because every row in a batch comes from the
     * same sheet via the same template, so {@code rowData} carries the same keys throughout; that
     * assumption already holds everywhere else this repo reads a batch's rows.
     */
    private final class CsvRowWriter {
        private final OutputStream out;
        private List<String> columns;
        private SequenceWriter writer;
        private long count;

        CsvRowWriter(OutputStream out) {
            this.out = out;
        }

        void write(ValidationServiceRowsResponse.Row row) {
            try {
                if (writer == null) {
                    init(row.rowData());
                }
                Map<String, Object> rowData = row.rowData();
                String[] values = new String[columns.size() + 3];
                values[0] = String.valueOf(row.rowNumber());
                for (int i = 0; i < columns.size(); i++) {
                    values[i + 1] = stringify(rowData == null ? null : rowData.get(columns.get(i)));
                }
                values[columns.size() + 1] = row.rowStatus();
                values[columns.size() + 2] = formatErrors(row.errors());
                writer.write(values);
                count++;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void init(Map<String, Object> firstRowData) throws IOException {
            columns = firstRowData == null ? List.of() : List.copyOf(firstRowData.keySet());
            CsvSchema.Builder schema = CsvSchema.builder().addColumn("row_number");
            columns.forEach(schema::addColumn);
            schema.addColumn("row_status").addColumn("errors");
            writer = new CsvMapper().writer(schema.setUseHeader(true).build()).writeValues(out);
        }

        void close() throws IOException {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    /** {@code field: message} pairs, joined for spreadsheet readability — friendlier than raw JSON in a CSV cell. */
    private String formatErrors(List<Map<String, Object>> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .map(e -> e.getOrDefault("field", "") + ": " + e.getOrDefault("errorMessage", ""))
                .collect(Collectors.joining(" | "));
    }

    private String putToS3(BatchUploadResult result, ExportOwner owner, UUID batchId, String filename, Path tempFile) {
        StorageConfig config = storageConfigRepository
                .findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        assertS3FieldsPresent(config);

        String key = UploadObjectKeys.validated(deploymentEnvironment.current(), HostContext.getCurrentTenant(),
                owner.processId(), owner.templateId(), batchId.toString(), filename);

        // Transfer-manager multipart upload, not a plain putObject — same reasoning as
        // UploadS3Worker#putToS3: a lakh-row batch's CSV can be large enough to benefit from
        // parallel part uploads instead of one single-stream PUT.
        try (S3AsyncClient asyncClient = S3ClientFactory.buildAsync(config);
             S3TransferManager transferManager = S3TransferManager.builder().s3Client(asyncClient).build()) {
            UploadFileRequest uploadRequest = UploadFileRequest.builder()
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(config.getBucketName())
                            .key(key)
                            .contentType("text/csv")
                            .build())
                    .source(tempFile)
                    .build();
            transferManager.uploadFile(uploadRequest).completionFuture().join();
            result.setResultS3Bucket(config.getBucketName());
            result.setResultS3Key(key);
            return key;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause instanceof S3Exception s3e && s3e.awsErrorDetails() != null
                    ? s3e.awsErrorDetails().errorMessage() : cause.getMessage();
            log.error("Validated-result S3 upload failed: bucket={}, key={}", config.getBucketName(), key, cause);
            throw new IllegalStateException("S3 upload failed: " + detail, cause);
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
