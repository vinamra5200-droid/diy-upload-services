package in.qualtechedge.qcp.templates.service.impl;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import in.qualtechedge.qcp.templates.dto.request.ConsumerCallbackBatchesResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.entity.UploadJobCallbackResult;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobCallbackResultRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.service.ConsumerCallbackResultsClient;
import in.qualtechedge.qcp.templates.utils.DeploymentEnvironment;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import in.qualtechedge.qcp.templates.utils.UploadFileRowReader;
import in.qualtechedge.qcp.templates.utils.UploadObjectKeys;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

/**
 * Builds a row-by-row CSV of a job's processed outcome — every column the dispatched sheet had,
 * plus {@code status} (PASSED/FAILED) and {@code api_response} appended — and writes it to S3 via
 * {@link UploadObjectKeys#processed}, the reverse-side counterpart of {@link ValidatedResultS3Exporter}
 * one stage further down the same {@code diy-upload/{env}/{tenantCode}/{processId}/{templateId}/{stage}/...}
 * key tree. There is no per-row delivery result to read back — a Kafka chunk was posted to the third
 * party as one atomic HTTP call, so {@link CallbackRowRanges} maps consumer-callback-service's
 * per-batch outcome back onto the contiguous row span that chunk covered, and every row in that span
 * gets the batch's shared status/response. Kicked off from
 * {@link in.qualtechedge.qcp.templates.controller.UploadJobCallbackController} after the completion
 * callback is recorded, and runs off that request thread for the same reason
 * {@link ValidatedResultS3Exporter} does. A failure here is logged only, never retried — the
 * aggregate summary already reached the maker by the time this runs, and
 * {@code UploadJobServiceImpl#download} falls back to the job's dispatched (pre-outcome) file if
 * this export never lands.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedResultS3Exporter {

    private final UploadJobRepository uploadJobRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final UploadJobCallbackResultRepository uploadJobCallbackResultRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final DeploymentEnvironment deploymentEnvironment;
    private final ConsumerCallbackResultsClient consumerCallbackResultsClient;

    @Async("uploadTaskExecutor")
    public void export(String tenant, String jobId) {
        // Same reasoning as ValidatedResultS3Exporter#export — this runs on a fresh thread pool
        // thread, which doesn't inherit the calling request thread's HostContext ThreadLocal.
        HostContext.setCurrentTenant(tenant);
        try {
            UploadJobCallbackResult result = uploadJobCallbackResultRepository.findById(jobId).orElse(null);
            Optional<UploadJob> job = uploadJobRepository.findById(jobId);
            ExportOwner owner = job.flatMap(this::resolveOwner).orElse(null);
            if (result == null || job.isEmpty() || owner == null) {
                log.warn("Skipping processed-result export — result or job owner not found: jobId={}", jobId);
                return;
            }

            List<ConsumerCallbackBatchesResponse.Batch> batches = new ArrayList<>();
            consumerCallbackResultsClient.streamBatches(jobId, tenant, batches::addAll);
            List<CallbackRowRanges.RowRange> ranges = CallbackRowRanges.build(batches);

            Path sourceFile = tempFilePath(jobId, "source");
            Path outputFile = tempFilePath(jobId, "processed");
            try {
                downloadDispatchedFile(job.get(), sourceFile);
                long rowCount = writeCsv(job.get(), ranges, sourceFile, outputFile);
                String filename = baseName(owner.originalFilename()) + "_processed.csv";
                putToS3(result, owner, jobId, filename, outputFile);
                uploadJobCallbackResultRepository.save(result);
                log.info("Processed result exported to S3: jobId={}, bucket={}, key={}, rowCount={}",
                        jobId, result.getResultS3Bucket(), result.getResultS3Key(), rowCount);
            } catch (RuntimeException e) {
                log.error("Processed result export failed: jobId={}", jobId, e);
            } finally {
                deleteQuietly(sourceFile);
                deleteQuietly(outputFile);
            }
        } finally {
            HostContext.clear();
        }
    }

    /** {@code UploadJob.uploadAttemptId} always resolves to an {@link UploadAttempt} — that's where the
     * process/template ids and original filename this key needs actually live; the job itself only
     * carries their human-readable codes. */
    private Optional<ExportOwner> resolveOwner(UploadJob job) {
        return uploadAttemptRepository.findById(job.getUploadAttemptId())
                .map(a -> new ExportOwner(a.getOriginalFilename(), a.getProcessId(), a.getTemplateId()));
    }

    private record ExportOwner(String originalFilename, String processId, String templateId) {
    }

    private void downloadDispatchedFile(UploadJob job, Path destination) {
        StorageConfig config = storageConfigRepository
                .findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        try (S3Client client = S3ClientFactory.build(config)) {
            client.getObject(GetObjectRequest.builder().bucket(config.getBucketName())
                    .key(job.getCompletedFileKey()).build(), destination);
        } catch (S3Exception e) {
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new IllegalStateException("S3 download failed: " + detail, e);
        }
    }

    /**
     * Reads the same file {@link in.qualtechedge.qcp.templates.service.impl.PostLoadActionDispatcherImpl}
     * dispatched ({@code job.getCompletedFileKey()}) row by row, looking up each row's status/response
     * off the {@link CallbackRowRanges} its position falls in — that position is exactly the
     * {@code rowNumber} {@code PostLoadActionDispatcherImpl} assigned when it originally chunked this
     * same file, so the two line up without needing to carry row numbers through Kafka at all.
     */
    private long writeCsv(UploadJob job, List<CallbackRowRanges.RowRange> ranges, Path sourceFile, Path outputFile) {
        UploadFormatKey format = UploadFileRowReader.detectFormat(filenameOf(job.getCompletedFileKey()));
        try (OutputStream out = Files.newOutputStream(outputFile)) {
            ProcessedRowWriter rowWriter = new ProcessedRowWriter(out);
            UploadFileRowReader.readRows(sourceFile, format,
                    (rowNumber, data) -> rowWriter.write(rowNumber, data, CallbackRowRanges.find(ranges, rowNumber)));
            rowWriter.close();
            return rowWriter.count;
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("Failed to write processed-result CSV for job " + job.getJobId(), e);
        }
    }

    /**
     * Header is the dispatched sheet's own columns (derived from the first row's keys, in their
     * original order — every row shares the same keys, since they all came off one chunked file)
     * plus {@code status} and {@code api_response} appended, mirroring
     * {@link ValidatedResultS3Exporter}'s {@code row_status}/{@code errors} convention for the
     * validated stage.
     */
    private final class ProcessedRowWriter {
        private final OutputStream out;
        private List<String> columns;
        private SequenceWriter writer;
        private long count;

        ProcessedRowWriter(OutputStream out) {
            this.out = out;
        }

        void write(int rowNumber, Map<String, Object> data, CallbackRowRanges.RowRange range) {
            try {
                if (writer == null) {
                    init(data);
                }
                String[] values = new String[columns.size() + 2];
                for (int i = 0; i < columns.size(); i++) {
                    values[i] = stringify(data == null ? null : data.get(columns.get(i)));
                }
                values[columns.size()] = range == null ? "" : range.status();
                values[columns.size() + 1] = formatResponse(range);
                writer.write(values);
                count++;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void init(Map<String, Object> firstRowData) throws IOException {
            columns = firstRowData == null ? List.of() : List.copyOf(firstRowData.keySet());
            CsvSchema.Builder schema = CsvSchema.builder();
            columns.forEach(schema::addColumn);
            schema.addColumn("status").addColumn("api_response");
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

    /** {@code "HTTP {status}: {body}"} — same shape {@code UploadJobServiceImpl#getJobRows} already
     * shows the maker on-screen, so the downloaded file and the table read the same way. */
    private String formatResponse(CallbackRowRanges.RowRange range) {
        if (range == null || range.responseText() == null) {
            return "";
        }
        return range.httpStatusCode() != null
                ? "HTTP " + range.httpStatusCode() + ": " + range.responseText()
                : range.responseText();
    }

    private void putToS3(UploadJobCallbackResult result, ExportOwner owner, String jobId, String filename, Path tempFile) {
        StorageConfig config = storageConfigRepository
                .findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        assertS3FieldsPresent(config);

        String key = UploadObjectKeys.processed(deploymentEnvironment.current(), HostContext.getCurrentTenant(),
                owner.processId(), owner.templateId(), jobId, filename);

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
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause instanceof S3Exception s3e && s3e.awsErrorDetails() != null
                    ? s3e.awsErrorDetails().errorMessage() : cause.getMessage();
            log.error("Processed-result S3 upload failed: bucket={}, key={}", config.getBucketName(), key, cause);
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

    private String filenameOf(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash < 0 ? objectKey : objectKey.substring(lastSlash + 1);
    }

    /** {@code source}/{@code processed} suffix keeps the downloaded-then-rewritten pair from
     * colliding on disk for the same job. */
    private Path tempFilePath(String jobId, String suffix) {
        return Path.of(System.getProperty("java.io.tmpdir"), "diy-upload-processed-" + jobId + "-" + suffix + "-" + UUID.randomUUID() + ".tmp");
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp processed-result file: {}", path, e);
        }
    }
}
