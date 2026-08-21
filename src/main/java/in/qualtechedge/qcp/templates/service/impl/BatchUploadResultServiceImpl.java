package in.qualtechedge.qcp.templates.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import in.qualtechedge.qcp.templates.dto.response.BatchResultRowResponse;
import in.qualtechedge.qcp.templates.dto.response.BatchUploadResultSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import in.qualtechedge.qcp.templates.entity.BatchUploadResultRow;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRepository;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRowRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.BatchUploadResultService;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchUploadResultServiceImpl implements BatchUploadResultService {

    private final UploadFileRepository uploadFileRepository;
    private final BatchUploadResultRepository batchUploadResultRepository;
    private final BatchUploadResultRowRepository batchUploadResultRowRepository;

    @Override
    @Transactional(readOnly = true)
    public BatchUploadResultSummaryResponse getSummary(String uploadId) {
        UUID batchId = resolveBatchId(uploadId);
        BatchUploadResult result = batchUploadResultRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation results are not available yet for upload " + uploadId));
        return new BatchUploadResultSummaryResponse(result.getBatchId(), result.getStatus(),
                result.getTotalRowsReceived(), result.getPassedCount(), result.getFailedCount(),
                result.getWarningCount(), result.getReceivedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BatchResultRowResponse> getRows(String uploadId, Pageable pageable) {
        UUID batchId = resolveBatchId(uploadId);
        Page<BatchResultRowResponse> page = batchUploadResultRowRepository.findByBatchIdOrderByRowNumberAsc(batchId, pageable)
                .map(this::toResponse);
        return PageResponse.from(page);
    }

    private UUID resolveBatchId(String uploadId) {
        UploadFile uploadFile = uploadFileRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found with id: " + uploadId));
        if (uploadFile.getJobId() == null) {
            throw new ResourceNotFoundException("Validation results are not available yet for upload " + uploadId);
        }
        return UUID.fromString(uploadFile.getJobId());
    }

    private BatchResultRowResponse toResponse(BatchUploadResultRow row) {
        Map<String, Object> rowData = JsonColumnMapper.read(row.getRowData(), new TypeReference<Map<String, Object>>() {
        });
        List<Map<String, Object>> errors = JsonColumnMapper.read(row.getErrors(), new TypeReference<List<Map<String, Object>>>() {
        });
        return new BatchResultRowResponse(row.getRowNumber(), rowData, errors);
    }
}
