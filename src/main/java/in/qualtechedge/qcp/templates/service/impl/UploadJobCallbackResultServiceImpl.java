package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.CallbackCompletedRequest;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.repository.UploadJobCallbackResultRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.UploadJobCallbackResultService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadJobCallbackResultServiceImpl implements UploadJobCallbackResultService {

    private final UploadJobRepository uploadJobRepository;
    private final UploadJobCallbackResultRepository uploadJobCallbackResultRepository;
    private final AuditEventService auditEventService;

    /**
     * Claims via {@link UploadJobCallbackResultRepository#claim} — the same row
     * {@link #recordCompletion} would otherwise build later, just moved ahead of the status
     * transition so a concurrent or retried duplicate of this callback is turned away here.
     */
    @Override
    @Transactional
    public boolean claim(CallbackCompletedRequest message) {
        int inserted = uploadJobCallbackResultRepository.claim(message.jobId(), message.status(),
                message.totalBatches(), message.successCount(), message.failedCount());
        if (inserted == 0) {
            log.info("Job callback-completed request ignored — already claimed: jobId={}", message.jobId());
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public void unclaim(String jobId) {
        uploadJobCallbackResultRepository.deleteById(jobId);
        log.warn("Job callback-completed claim released after a failed recordCompletion: jobId={}", jobId);
    }

    @Override
    @Transactional
    public void recordCompletion(CallbackCompletedRequest message) {
        UploadJob job = uploadJobRepository.findById(message.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + message.jobId()));

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.JOB_CALLBACK_COMPLETED, job.getMakerUserId(), null, job.getProcessCode(),
                job.getTemplateCode(), job.getTemplateVersion(), null, job.getUploadAttemptId(), job.getSubmissionId(),
                job.getJobId(), AuditOutcome.SUCCESS,
                "Callback delivery completed: " + message.successCount() + " succeeded, " + message.failedCount() + " failed",
                Map.of("totalBatches", message.totalBatches(), "successCount", message.successCount(),
                        "failedCount", message.failedCount())));

        // JobStatus has no partial-success state today, so any permanently failed batch marks the
        // whole job FAILED rather than COMPLETED — the granular split still lives on
        // upload_job_callback_results (total/success/failed counts) for the UI to show.
        job.setStatus(message.failedCount() > 0 ? JobStatus.FAILED : JobStatus.COMPLETED);
        uploadJobRepository.save(job);
    }
}
