package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedMessage;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceFailedRowsResponse;
import java.util.List;

/**
 * Business logic behind {@link in.qualtechedge.qcp.templates.consumer.BatchValidationCompletedListener}:
 * records the {@code VALIDATION_COMPLETED} audit event, persists the already-fetched row-wise
 * results locally, and releases the process's config lock — all in one transaction.
 */
public interface BatchValidationResultService {

    void recordCompletion(BatchValidationCompletedMessage message, List<ValidationServiceFailedRowsResponse.Row> failedRows);
}
