package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.UploadCountsResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadFileResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Accepts a maker's raw upload file, checksums and stages it, then hands the actual S3 PUT off
 * to a background worker so the HTTP call returns without waiting for the transfer — track
 * progress via {@link #subscribe} (SSE) instead of polling {@link #getById}.
 * <p>
 * Object key layout: {@code diy-upload/{env}/{processId}/{templateId}/raw/{filename}} — see
 * {@link in.qualtechedge.qcp.templates.service.impl.UploadS3Worker} for the exact rules.
 */
public interface S3UploadService {

    UploadFileResponse upload(String processId, String templateId, MultipartFile file);

    UploadFileResponse getById(String uploadId);

    /** Push channel for one upload's status changes; completes once a terminal status is reached. */
    SseEmitter subscribe(String uploadId);

    /**
     * Dashboard counts for one process — completed/failed are all-time totals, pending/inProgress
     * are current state. {@code templateId} narrows to one template within the process; null
     * means every template under it.
     */
    UploadCountsResponse counts(String processId, String templateId);
}
