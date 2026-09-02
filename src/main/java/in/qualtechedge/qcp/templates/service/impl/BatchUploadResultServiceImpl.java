package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.response.BatchUploadResultSummaryResponse;
import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.BatchUploadResultService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchUploadResultServiceImpl implements BatchUploadResultService {

    private final UploadFileRepository uploadFileRepository;
    private final BatchUploadResultRepository batchUploadResultRepository;

    @Override
    @Transactional(readOnly = true)
    public BatchUploadResultSummaryResponse getSummary(String uploadId) {
        UUID batchId = resolveBatchId(uploadId);
        BatchUploadResult result = batchUploadResultRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation results are not available yet for upload " + uploadId));
        return new BatchUploadResultSummaryResponse(result.getBatchId(), result.getStatus(),
                result.getTotalRowsReceived(), result.getPassedCount(), result.getFailedCount(),
                result.getReceivedAt(), result.getResultS3Bucket(), result.getResultS3Key());
    }

    private UUID resolveBatchId(String uploadId) {
        UploadFile uploadFile = uploadFileRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found with id: " + uploadId));
        if (uploadFile.getJobId() == null) {
            throw new ResourceNotFoundException("Validation results are not available yet for upload " + uploadId);
        }
        return UUID.fromString(uploadFile.getJobId());
    }
}
