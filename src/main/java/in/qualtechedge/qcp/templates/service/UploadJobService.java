package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import java.util.List;

/** §3.2, §3.3 (job half) — maker-side tracking of jobs their submissions/attempts produced. */
public interface UploadJobService {

    List<UploadJobResponse> listByMaker(String makerUserId);

    PresignedDownloadResponse download(String jobId, String actorId);
}
