package in.qualtechedge.qcp.templates.dto.response;

import java.util.List;
import java.util.Map;

/**
 * A single validated row, pass or fail — one page of the maker's on-demand results browsing
 * (upload-api-contract.md, rows endpoints). Field-for-field mirror of validation-service's own
 * {@code BatchRowResponse}, fetched fresh per page rather than mirrored locally (see
 * {@code UploadAttemptController#getRows}).
 */
public record ValidationRowResponse(
        Integer rowNumber,
        Map<String, Object> rowData,
        List<Map<String, Object>> errors,
        String rowStatus
) {
}
