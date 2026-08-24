package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.ProceedResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** §2 Upload Attempts. */
public interface UploadAttemptService {

    UploadAttemptResponse create(String processId, String templateId, String actorId, MultipartFile file);

    UploadAttemptResponse startValidation(String attemptId, String actorId);

    UploadAttemptResponse get(String attemptId);

    ProceedResponse proceed(String attemptId, String actorId);

    UploadAttemptResponse reupload(String attemptId, String actorId);

    List<UploadAttemptResponse> listByMaker(String makerUserId);

    PresignedDownloadResponse download(String attemptId, String stage, String actorId);
}
