package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.CallbackCompletedRequest;

/**
 * Business logic behind {@link in.qualtechedge.qcp.templates.controller.UploadJobCallbackController}:
 * records the {@code JOB_CALLBACK_COMPLETED} audit event and moves the job out of {@code PROCESSING}
 * once consumer-callback-service reports every batch attempted. Mirrors
 * {@code BatchValidationResultService} (diy-validation-service's own completion-callback handler)
 * for this, outbound-API-delivery leg of the pipeline instead of the validation leg.
 */
public interface UploadJobCallbackResultService {

    /**
     * Claims this job's callback completion for processing: {@code true} means the caller is the
     * first (and only) one to record it and should proceed to {@link #recordCompletion}; {@code
     * false} means another call — a concurrent duplicate or a later retry — already claimed it, and
     * the caller should treat the request as an already-handled no-op instead of redoing the work.
     */
    boolean claim(CallbackCompletedRequest message);

    /** Records completion. Only ever called after a successful {@link #claim}. */
    void recordCompletion(CallbackCompletedRequest message);

    /**
     * Reverses a {@link #claim} that didn't make it to a recorded completion (an exception thrown
     * from {@link #recordCompletion}) — otherwise the claim would stick around forever and every
     * later retry of a job whose callback simply failed once would be turned away as
     * "already handled", with the job stuck in {@code PROCESSING} forever.
     */
    void unclaim(String jobId);
}
