package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.entity.UploadFile;
import java.nio.file.Path;

/**
 * Publishes a completed upload's rows to validation-service's {@code data-validation-requested-topic}, in
 * chunks keyed by the upload's job id. Called from {@link in.qualtechedge.qcp.templates.service.impl.UploadS3Worker}
 * right after the S3 write completes, while the staged temp file still exists.
 */
public interface BatchChunkPublisher {

    /**
     * Never throws — the S3 upload {@code record} reports on has already succeeded by the time
     * this runs, so a parse or Kafka failure here is recorded as an {@code ENQUEUE_FAILED} audit
     * event and swallowed, not surfaced back to the caller.
     */
    void publish(UploadFile record, Path file);
}
