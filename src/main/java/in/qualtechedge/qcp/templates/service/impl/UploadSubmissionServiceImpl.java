package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadSubmissionResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadSubmission;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadSubmissionMapper;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.UploadAttemptRepository;
import in.qualtechedge.qcp.templates.repository.UploadSubmissionRepository;
import in.qualtechedge.qcp.templates.service.UploadSubmissionService;
import in.qualtechedge.qcp.templates.utils.S3ClientFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadSubmissionServiceImpl implements UploadSubmissionService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final UploadSubmissionRepository uploadSubmissionRepository;
    private final UploadAttemptRepository uploadAttemptRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final UploadSubmissionMapper uploadSubmissionMapper;

    @Override
    @Transactional(readOnly = true)
    public List<UploadSubmissionResponse> listByMaker(String makerUserId) {
        return uploadSubmissionRepository.findByMakerUserIdOrderByCreatedAtDesc(makerUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedDownloadResponse download(String submissionId, String actorId) {
        UploadSubmission submission = findOrThrow(submissionId);
        if (!submission.getMakerUserId().equals(actorId)) {
            throw new AccessDeniedException("Submission " + submissionId + " does not belong to actor " + actorId);
        }
        StorageConfig config = storageConfigRepository.findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        PresignedGetObjectRequest presigned = S3ClientFactory.presign(config, submission.getPendingObjectKey(), DOWNLOAD_URL_EXPIRY);
        return new PresignedDownloadResponse(presigned.url().toString(), OffsetDateTime.now().plus(DOWNLOAD_URL_EXPIRY));
    }

    private UploadSubmissionResponse toResponse(UploadSubmission submission) {
        String templateId = uploadAttemptRepository.findById(submission.getUploadAttemptId())
                .map(a -> a.getTemplateId()).orElse(null);
        return uploadSubmissionMapper.toResponse(submission, templateId);
    }

    private UploadSubmission findOrThrow(String submissionId) {
        return uploadSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));
    }
}
