package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.ConsumerCallbackBatchesResponse;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pulls per-batch delivery detail from consumer-callback-service's REST API — the on-demand
 * drill-down behind a job's aggregate {@code callback-summary}. Mirrors
 * {@link ValidationServiceResultsClient}'s shape for the same reason: batch-level detail
 * intentionally isn't copied into diy-upload-services' own database, so a maker's click-driven
 * request pulls exactly one page, live, rather than diy-upload-services keeping its own stale copy.
 */
public interface ConsumerCallbackResultsClient {

    /**
     * Fetches exactly the one page requested. {@code outcome} is optional; {@code null}/blank
     * means unfiltered (every batch, any outcome).
     */
    ConsumerCallbackBatchesResponse.Data fetchBatchesPage(String jobId, String tenantCode, String outcome,
            int page, int size);

    /**
     * Pages through the batches endpoint until exhausted, handing each page to {@code pageHandler}
     * as it arrives instead of accumulating the whole job in memory — same reasoning as
     * {@link ValidationServiceResultsClient#streamRows}. Used only for the processed-result export
     * ({@code ProcessedResultS3Exporter}), which genuinely needs every batch; the maker's
     * interactive drill-down uses {@link #fetchBatchesPage} instead, one page at a time.
     */
    void streamBatches(String jobId, String tenantCode, Consumer<List<ConsumerCallbackBatchesResponse.Batch>> pageHandler);
}
