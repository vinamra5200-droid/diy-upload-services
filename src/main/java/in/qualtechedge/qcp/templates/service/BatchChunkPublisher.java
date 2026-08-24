package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.BatchPublishRequest;
import java.nio.file.Path;

/**
 * Publishes a completed upload's rows to validation-service's {@code data-validation-requested-topic}, in
 * chunks keyed by the batch id. Called from {@link in.qualtechedge.qcp.templates.service.impl.UploadS3Worker}
 * right after the S3 write completes (raw-upload flow) and from the upload-attempt flow's
 * {@code UploadAttemptServiceImpl#startValidation}, both while the source file still exists locally.
 */
public interface BatchChunkPublisher {

    /**
     * Never throws — the S3 upload {@code request} reports on has already succeeded by the time
     * this runs, so a parse or Kafka failure here is recorded as an {@code ENQUEUE_FAILED} audit
     * event rather than surfaced as an exception. Returns {@code false} on that failure (instead
     * of {@code true}) so the caller can still react to it.
     */
    boolean publish(BatchPublishRequest request, Path file);
}
