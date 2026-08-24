package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.response.PresignedDownloadResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadJobResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadJobMapper;
import in.qualtechedge.qcp.templates.repository.StorageConfigRepository;
import in.qualtechedge.qcp.templates.repository.UploadJobRepository;
import in.qualtechedge.qcp.templates.service.UploadJobService;
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
public class UploadJobServiceImpl implements UploadJobService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final UploadJobRepository uploadJobRepository;
    private final StorageConfigRepository storageConfigRepository;
    private final UploadJobMapper uploadJobMapper;

    @Override
    @Transactional(readOnly = true)
    public List<UploadJobResponse> listByMaker(String makerUserId) {
        return uploadJobRepository.findByMakerUserIdOrderByCreatedAtDesc(makerUserId).stream()
                .map(uploadJobMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedDownloadResponse download(String jobId, String actorId) {
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        if (!job.getMakerUserId().equals(actorId)) {
            throw new AccessDeniedException("Job " + jobId + " does not belong to actor " + actorId);
        }
        StorageConfig config = storageConfigRepository.findFirstByProviderAndStatus(InterimStoreProvider.AWS_S3, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active AWS_S3 storage connection is configured"));
        PresignedGetObjectRequest presigned = S3ClientFactory.presign(config, job.getCompletedFileKey(), DOWNLOAD_URL_EXPIRY);
        return new PresignedDownloadResponse(presigned.url().toString(), OffsetDateTime.now().plus(DOWNLOAD_URL_EXPIRY));
    }
}
