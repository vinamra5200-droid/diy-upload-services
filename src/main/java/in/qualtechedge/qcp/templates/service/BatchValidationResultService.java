package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedRequest;
import java.util.UUID;

/**
 * Business logic behind {@link in.qualtechedge.qcp.templates.controller.BatchUploadController}:
 * records the {@code VALIDATION_COMPLETED} audit event, persists the row-wise results streamed in
 * from validation-service (every row, pass or fail), and releases the process's config lock.
 */
public interface BatchValidationResultService {

    /**
     * Claims this batch for processing: {@code true} means the caller is the first (and only) one
     * to record it and should proceed to {@link #recordCompletion}; {@code false} means another
     * call — a concurrent duplicate or a later retry — already claimed it, and the caller should
     * treat the request as an already-handled no-op instead of redoing the work.
     */
    boolean claim(BatchValidationCompletedRequest message);

    /** Pulls the batch's rows from validation-service and records completion. Only ever called after a successful {@link #claim}. */
    void recordCompletion(BatchValidationCompletedRequest message);

    /**
     * Reverses a {@link #claim} that didn't make it to a recorded completion (an exception thrown
     * from {@link #recordCompletion}) — otherwise the claim would stick around forever and every
     * later retry of a batch that simply failed once would be turned away as "already handled",
     * with nothing ever actually recorded for it.
     */
    void unclaim(UUID batchId);
}
