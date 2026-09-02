package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.AcceptSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** §4 Checker (upload-operator review). */
public interface CheckerService {

    List<UploadSubmissionResponse> inbox(String checkerId);

    UploadSubmissionResponse get(String submissionId);

    /** Same on-demand rows browsing as {@code UploadAttemptService#getRows}, resolved via this
     * submission's originating attempt's batchId. */
    PageResponse<ValidationRowResponse> getRows(String submissionId, String rowStatus, List<String> ruleTypes,
            String search, Pageable pageable);

    PresignedDownloadResponse download(String submissionId, String checkerId);

    AcceptSubmissionResponse accept(String submissionId, String checkerId);

    UploadSubmissionResponse reject(String submissionId, String checkerId, RejectRequest request);
}
