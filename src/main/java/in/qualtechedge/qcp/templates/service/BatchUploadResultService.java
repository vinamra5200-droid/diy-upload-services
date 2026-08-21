package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.BatchResultRowResponse;
import in.qualtechedge.qcp.templates.dto.response.BatchUploadResultSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Read side of {@code batch_upload_results}/{@code batch_upload_result_rows} — the local copy of
 * validation-service's outcome, populated by {@link BatchValidationResultService}. This is what
 * diy-upload-web actually calls; it never reaches validation-service directly.
 */
public interface BatchUploadResultService {

    /**
     * @throws in.qualtechedge.qcp.templates.exception.ResourceNotFoundException if the upload's
     *         batch hasn't finished validation yet (no completion event received)
     */
    BatchUploadResultSummaryResponse getSummary(String uploadId);

    PageResponse<BatchResultRowResponse> getRows(String uploadId, Pageable pageable);
}
