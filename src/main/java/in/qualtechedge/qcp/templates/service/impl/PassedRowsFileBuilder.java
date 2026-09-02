package in.qualtechedge.qcp.templates.service.impl;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
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
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

/**
 * Builds the clean, passed-rows-only file a job actually dispatches — called synchronously, inline,
 * at the two places a job is ever created ({@code UploadAttemptServiceImpl#createDirectJob},
 * {@code CheckerServiceImpl#accept}), right before the {@link UploadAttempt} in hand becomes a
 * {@code UploadJob}. Deliberately distinct from {@link UploadObjectKeys#validated}: that stage keeps
 * every row (pass or fail) plus {@code row_status}/{@code errors}, for the maker's own review and
 * download, and must never be read for dispatch — a job built from it would re-send rows that had
 * already failed validation to the third party, which is exactly the bug this class exists to close.
 * <p>
 * Streams straight from validation-service ({@link ValidationServiceResultsClient#streamRows}), the
 * same source {@code ValidatedResultS3Exporter} reads, filtering to {@code PASSED} rows only. The
 * header is captured off the first row seen regardless of status, so a batch with zero passed rows
 * still writes a well-formed (header-only) file instead of an empty one {@code UploadFileRowReader}
 * would choke on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PassedRowsFileBuilder {

    private final ValidationServiceResultsClient validationServiceResultsClient;
    private final StorageConfigRepository storageConfigRepository;
    private final DeploymentEnvironment deploymentEnvironment;

    /**
     * Returns the S3 key of the newly built passed-only CSV, or {@code fallbackKey} unchanged when
     * {@code attempt.getBatchId()} is null — validation was skipped for this template, so every row
     * already counts as passed and there is nothing to filter out of {@code fallbackKey}.
     */
    public String build(UploadAttempt attempt, String fallbackKey) {
        if (attempt.getBatchId() == null) {
            return fallbackKey;
        }
        Path tempFile = tempFilePath(attempt.getUploadAttemptId());
        try {
            writeCsv(attempt, tempFile);
            String filename = baseName(attempt.getOriginalFilename()) + "_passed.csv";
            String key = putToS3(attempt, filename, tempFile);
            log.info("Passed-rows dispatch file built: attemptId={}, batchId={}, key={}",
                    attempt.getUploadAttemptId(), attempt.getBatchId(), key);
            return key;
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private void writeCsv(UploadAttempt attempt, Path tempFile) {
        try (OutputStream out = Files.newOutputStream(tempFile)) {
            PassedRowWriter rowWriter = new PassedRowWriter(out);
            validationServiceResultsClient.streamRows(attempt.getBatchId(), HostContext.getCurrentTenant(),
                    page -> page.forEach(rowWriter::write));
            rowWriter.close();
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("Failed to write passed-rows dispatch CSV for attempt "
                    + attempt.getUploadAttemptId(), e);
        }
    }

    /**
     * Header columns come from the first row seen, any status — otherwise a batch with zero passed
     * rows would write a truly empty file, which {@code UploadFileRowReader} can't parse a header
     * out of. Only {@code PASSED} rows are actually written below that header.
     */
    private final class PassedRowWriter {
        private final OutputStream out;
        private List<String> columns;
        private SequenceWriter writer;

        PassedRowWriter(OutputStream out) {
            this.out = out;
        }

        void write(ValidationServiceRowsResponse.Row row) {
            try {
                if (writer == null) {
                    init(row.rowData());
                }
                if (!"PASSED".equals(row.rowStatus())) {
                    return;
                }
                Map<String, Object> rowData = row.rowData();
                String[] values = new String[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    values[i] = stringify(rowData == null ? null : rowData.get(columns.get(i)));
                }
                writer.write(values);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void init(Map<String, Object> firstRowData) throws IOException {
            columns = firstRowData == null ? List.of() : List.copyOf(firstRowData.keySet());
            CsvSchema.Builder schema = CsvSchema.builder();
            columns.forEach(schema::addColumn);
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

    private String putToS3(UploadAttempt attempt, String filename, Path tempFile) {
        StorageConfig config = storageConfigRepository
                .findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        assertS3FieldsPresent(config);

        String key = UploadObjectKeys.dispatch(deploymentEnvironment.current(), HostContext.getCurrentTenant(),
                attempt.getProcessId(), attempt.getTemplateId(), attempt.getUploadAttemptId(), filename);

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
            return key;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause instanceof S3Exception s3e && s3e.awsErrorDetails() != null
                    ? s3e.awsErrorDetails().errorMessage() : cause.getMessage();
            log.error("Passed-rows dispatch file upload failed: bucket={}, key={}", config.getBucketName(), key, cause);
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

    private Path tempFilePath(String attemptId) {
        return Path.of(System.getProperty("java.io.tmpdir"), "diy-upload-dispatch-" + attemptId + ".tmp");
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp dispatch file: {}", path, e);
        }
    }
}
