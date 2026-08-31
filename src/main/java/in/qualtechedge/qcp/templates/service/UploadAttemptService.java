package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.ProceedResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** §2 Upload Attempts. */
public interface UploadAttemptService {

    UploadAttemptResponse create(String processId, String templateId, String actorId, MultipartFile file);

    UploadAttemptResponse startValidation(String attemptId, String actorId);

    UploadAttemptResponse get(String attemptId);

    /**
     * On-demand (click-driven, not pre-fetched) rows browsing for this attempt's validation
     * result — fetches exactly the one page requested, straight from validation-service, every
     * filter optional: {@code rowStatus} (PASSED/FAILED), {@code ruleTypes} (rows that failed at
     * least one rule of one of these types), {@code search} (free text against the row's data).
     */
    PageResponse<ValidationRowResponse> getRows(String attemptId, String rowStatus, List<String> ruleTypes,
            String search, Pageable pageable);

    /** §2.2/§2.2a: SSE stream of this attempt's status — {@code attempt} events while it's still
     * ACCEPTED/VALIDATING, one {@code done} event (then the stream closes) once it isn't. 404s
     * immediately, before any stream is opened, if attemptId doesn't exist. */
    SseEmitter subscribe(String attemptId);

    ProceedResponse proceed(String attemptId, String actorId);

    UploadAttemptResponse reupload(String attemptId, String actorId);

    List<UploadAttemptResponse> listByMaker(String makerUserId);

    PresignedDownloadResponse download(String attemptId, String stage, String actorId);
}
