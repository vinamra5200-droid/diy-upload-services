package in.qualtechedge.qcp.templates.dto.response;

import java.util.List;
import java.util.Map;

/**
 * One row of a batch, as validation-service originally reported it — every row, pass or fail
 * (row-wise result display for diy-upload-web).
 */
public record BatchResultRowResponse(
        Integer rowNumber,
        Map<String, Object> rowData,
        List<Map<String, Object>> errors,
        String rowStatus
) {
}
