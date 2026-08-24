package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import java.util.List;
import java.util.UUID;

/**
 * Pulls row-wise validation results from validation-service's REST API once a batch completes —
 * the only outbound HTTP call this repo makes to that service (everything else is Kafka).
 */
public interface ValidationServiceResultsClient {

    /** Pages through the rows endpoint until exhausted, returning every row — pass and fail. */
    List<ValidationServiceRowsResponse.Row> fetchAllRows(UUID batchId);
}
