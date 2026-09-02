package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.CallbackBatchResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobCallbackSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRowResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** §3.2, §3.3 (job half) — maker-side tracking of jobs their submissions/attempts produced. */
public interface UploadJobService {

    List<UploadJobResponse> listByMaker(String makerUserId);

    /**
     * Single job by id — backs the maker UI's completion poll ({@code GET /jobs/{jobId}}), called
     * every 1.5s from job creation until it leaves {@code QUEUED}/{@code PROCESSING}.
     *
     * @throws in.qualtechedge.qcp.templates.exception.ResourceNotFoundException if the job doesn't
     *         exist or doesn't belong to {@code actorId}
     */
    UploadJobResponse getById(String jobId, String actorId);

    PresignedDownloadResponse download(String jobId, String actorId);

    /**
     * Starts streaming a {@code QUEUED} job's completed file to its template's configured Kafka
     * topic. Validates ownership, that the job hasn't already been dispatched, and that the
     * template's post-load action is actually {@code kafka}, then flips the job to
     * {@code PROCESSING} and hands the real work to a background worker — the returned response
     * reflects {@code PROCESSING}, not the eventual outcome.
     */
    UploadJobResponse dispatch(String jobId, String actorId);

    /**
     * Aggregate outcome of consumer-callback-service's outbound-API delivery for this job's
     * batches — total/success/failed batch counts, not row-by-row detail (that stays in
     * consumer-callback-service's own database).
     *
     * @throws in.qualtechedge.qcp.templates.exception.ResourceNotFoundException if the job doesn't
     *         belong to {@code actorId}, or its callback hasn't completed yet (no completion
     *         callback received)
     */
    UploadJobCallbackSummaryResponse getCallbackSummary(String jobId, String actorId);

    /**
     * Per-batch delivery detail behind the aggregate {@link #getCallbackSummary} — fetched live
     * from consumer-callback-service on every call (see {@link ConsumerCallbackResultsClient}),
     * never persisted here. {@code outcome} is optional; {@code null}/blank means unfiltered.
     *
     * @throws in.qualtechedge.qcp.templates.exception.ResourceNotFoundException if the job doesn't
     *         belong to {@code actorId}
     */
    PageResponse<CallbackBatchResponse> getCallbackBatches(String jobId, String actorId, String outcome, Pageable pageable);

    /**
     * On-demand (click-driven) row browsing for this job's processed-stage outcome — the maker-side
     * equivalent of {@link in.qualtechedge.qcp.templates.service.UploadAttemptService#getRows} for
     * the processed stage. There is no per-row delivery result: a Kafka chunk is posted to the
     * third-party as one atomic HTTP call, so every original source row within that chunk shares its
     * outcome and API response. This synthesizes one {@link ValidationRowResponse} per source row
     * from consumer-callback-service's per-batch detail ({@link ConsumerCallbackResultsClient#streamBatches})
     * rather than re-reading the job's file, using each batch's {@code rowCount} to place rows in
     * order. {@code rowStatus} (PASSED/FAILED) and {@code search} (free text against the row's API
     * response) are both optional.
     *
     * @throws in.qualtechedge.qcp.templates.exception.ResourceNotFoundException if the job doesn't
     *         belong to {@code actorId}
     */
    PageResponse<ValidationRowResponse> getJobRows(String jobId, String actorId, String rowStatus, String search,
            Pageable pageable);
}
