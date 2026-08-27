package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import java.util.List;

/** §3.2, §3.3 (job half) — maker-side tracking of jobs their submissions/attempts produced. */
public interface UploadJobService {

    List<UploadJobResponse> listByMaker(String makerUserId);

    PresignedDownloadResponse download(String jobId, String actorId);

    /**
     * Starts streaming a {@code QUEUED} job's completed file to its template's configured Kafka
     * topic. Validates ownership, that the job hasn't already been dispatched, and that the
     * template's post-load action is actually {@code kafka}, then flips the job to
     * {@code PROCESSING} and hands the real work to a background worker — the returned response
     * reflects {@code PROCESSING}, not the eventual outcome.
     */
    UploadJobResponse dispatch(String jobId, String actorId);
}
