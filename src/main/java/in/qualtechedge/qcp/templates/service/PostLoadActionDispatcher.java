package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.UploadJob;

/**
 * Streams a job's completed file (the {@code pending_processing} / stage-4 copy at
 * {@link UploadJob#getCompletedFileKey()}) to the topic named by {@code template.kafkaTopic}, in
 * chunks keyed by the job id. Called only after {@link UploadJobService#dispatch} has already
 * flipped the job to {@code PROCESSING} and confirmed {@code template.postLoadActionType == kafka}
 * — this never re-checks either.
 */
public interface PostLoadActionDispatcher {

    /**
     * Never throws — the job already exists and is already {@code PROCESSING} by the time this
     * runs, so an S3 or Kafka failure here is recorded as a {@code JOB_DISPATCH_FAILED} audit
     * event (and the job moved to {@code FAILED}) rather than surfaced as an exception. Returns
     * {@code false} on that failure (instead of {@code true}) so the caller can still react to it.
     */
    boolean dispatch(UploadJob job, Template template);
}
