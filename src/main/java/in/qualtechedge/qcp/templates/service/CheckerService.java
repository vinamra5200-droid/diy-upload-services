package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.AcceptSubmissionResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import java.util.List;

/** §4 Checker (upload-operator review). */
public interface CheckerService {

    List<UploadSubmissionResponse> inbox(String checkerId);

    UploadSubmissionResponse get(String submissionId);

    PresignedDownloadResponse download(String submissionId, String checkerId);

    AcceptSubmissionResponse accept(String submissionId, String checkerId);

    UploadSubmissionResponse reject(String submissionId, String checkerId, RejectRequest request);
}
