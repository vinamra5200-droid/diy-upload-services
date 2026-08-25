package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import java.util.List;

/**
 * Business logic behind {@link in.qualtechedge.qcp.templates.controller.BatchUploadController}:
 * records the {@code VALIDATION_COMPLETED} audit event, persists the already-fetched row-wise
 * results locally (every row, pass or fail), and releases the process's config lock — all in one
 * transaction.
 */
public interface BatchValidationResultService {

    void recordCompletion(BatchValidationCompletedRequest message, List<ValidationServiceRowsResponse.Row> rows);
}
