package in.qualtechedge.qcp.templates.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Shape of validation-service's {@code GET /api/v1/internal/batch-uploads/{batchId}/rows} response (its
 * locked {@code APIResponse<PageResponse<BatchRowResponse>>} envelope), as consumed by
 * {@link in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient}. Every row, pass or
 * fail — unlike the {@code /failed-rows} endpoint this replaced, which only ever returned failures.
 * Only the fields this repo actually reads are declared — {@code @JsonIgnoreProperties} covers the
 * rest of the envelope (errorCode/errorMessage/path/errors/timestamp) regardless of the caller's
 * Jackson fail-on-unknown-properties setting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValidationServiceRowsResponse(
        String status,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            List<Row> content,
            PageMeta page
    ) {
    }

    public record Row(
            Integer rowNumber,
            Map<String, Object> rowData,
            List<Map<String, Object>> errors,
            String rowStatus
    ) {
    }

    public record PageMeta(
            Integer number,
            Integer size,
            Long totalElements,
            Integer totalPages
    ) {
    }
}
