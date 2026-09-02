package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.BatchUploadResultSummaryResponse;

/**
 * Read side of {@code batch_upload_results} — the local copy of validation-service's outcome
 * summary, populated by {@link BatchValidationResultService}. Row-wise results are no longer
 * mirrored locally (see that service's {@code recordCompletion}) — they're fetched on demand
 * straight from validation-service instead (see {@code UploadAttemptController#getRows}).
 */
public interface BatchUploadResultService {

    /**
     * @throws in.qualtechedge.qcp.templates.exception.ResourceNotFoundException if the upload's
     *         batch hasn't finished validation yet (no completion event received)
     */
    BatchUploadResultSummaryResponse getSummary(String uploadId);
}
