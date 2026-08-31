package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Pulls row-wise validation results from validation-service's REST API — the only outbound HTTP
 * call this repo makes to that service (everything else is Kafka).
 */
public interface ValidationServiceResultsClient {

    /**
     * Pages through the rows endpoint until exhausted, handing each page (pass and fail rows
     * alike) to {@code pageHandler} as it arrives instead of accumulating the whole batch in
     * memory — a batch can run to lakhs of rows, and holding all of them (plus their per-row
     * {@code Map} payloads) in one {@code List} for the duration of the pull is what turns a
     * concurrent retry storm into an {@code OutOfMemoryError}. Used only for the CSV export
     * ({@code ValidatedResultS3Exporter}), which genuinely needs every row — the maker's
     * interactive results browsing uses {@link #fetchRowsPage} instead, one page at a time.
     */
    void streamRows(UUID batchId, String tenantCode, Consumer<List<ValidationServiceRowsResponse.Row>> pageHandler);

    /**
     * Fetches exactly the one page requested — the on-demand counterpart to {@link #streamRows}.
     * Backs the maker's click-driven results table: a page turn, a search, or a filter change each
     * triggers exactly one call here, never a pull of the whole batch. Every filter is optional;
     * {@code null}/empty means unfiltered.
     */
    ValidationServiceRowsResponse.Data fetchRowsPage(UUID batchId, String tenantCode, String rowStatus,
            List<String> ruleTypes, String search, int page, int size);
}
