package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import java.util.List;

/** §3.1, §3.3 (submission half) — maker-side tracking of their own submissions. */
public interface UploadSubmissionService {

    List<UploadSubmissionResponse> listByMaker(String makerUserId);

    PresignedDownloadResponse download(String submissionId, String actorId);
}
