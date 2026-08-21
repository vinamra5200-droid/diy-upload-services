package in.qualtechedge.qcp.templates.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Shape of validation-service's {@code GET /api/v1/batch-uploads/{batchId}/failed-rows} response
 * (its locked {@code APIResponse<PageResponse<FailedRowResponse>>} envelope), as consumed by
 * {@link in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient}. Only the fields
 * this repo actually reads are declared — {@code @JsonIgnoreProperties} covers the rest of the
 * envelope (errorCode/errorMessage/path/errors/timestamp) regardless of the caller's Jackson
 * fail-on-unknown-properties setting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValidationServiceFailedRowsResponse(
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
            List<Map<String, Object>> errors
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
