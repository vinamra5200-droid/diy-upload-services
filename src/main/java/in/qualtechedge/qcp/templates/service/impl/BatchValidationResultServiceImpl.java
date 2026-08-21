package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedMessage;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceFailedRowsResponse;
import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import in.qualtechedge.qcp.templates.entity.BatchUploadResultRow;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRepository;
import in.qualtechedge.qcp.templates.repository.BatchUploadResultRowRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.BatchValidationResultService;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchValidationResultServiceImpl implements BatchValidationResultService {

    private final UploadFileRepository uploadFileRepository;
    private final BatchUploadResultRepository batchUploadResultRepository;
    private final BatchUploadResultRowRepository batchUploadResultRowRepository;
    private final AuditEventService auditEventService;
    private final ConfigLockService configLockService;

    @Override
    @Transactional
    public void recordCompletion(BatchValidationCompletedMessage message, List<ValidationServiceFailedRowsResponse.Row> failedRows) {
        UploadFile uploadFile = uploadFileRepository.findFirstByJobId(message.batchId().toString())
                .orElseThrow(() -> new ResourceNotFoundException("No upload_files row for jobId " + message.batchId()));

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.VALIDATION_COMPLETED, uploadFile.getUploadedBy(), null, uploadFile.getProcessId(),
                uploadFile.getTemplateId(), null, null, null, null, message.batchId().toString(),
                AuditOutcome.SUCCESS,
                "Validation completed: " + message.passedCount() + " passed, " + message.failedCount() + " failed",
                Map.of("totalRowsReceived", message.totalRowsReceived(), "passedCount", message.passedCount(),
                        "failedCount", message.failedCount())));

        BatchUploadResult result = new BatchUploadResult();
        result.setBatchId(message.batchId());
        result.setProcessId(uploadFile.getProcessId());
        result.setTemplateId(uploadFile.getTemplateId());
        result.setStatus(message.status());
        result.setTotalRowsReceived(message.totalRowsReceived());
        result.setPassedCount(message.passedCount());
        result.setFailedCount(message.failedCount());
        result.setWarningCount(message.warningCount());
        batchUploadResultRepository.save(result);

        List<BatchUploadResultRow> rows = failedRows.stream().map(row -> {
            BatchUploadResultRow entity = new BatchUploadResultRow();
            entity.setId(IdGenerator.generate("bres"));
            entity.setBatchId(message.batchId());
            entity.setRowNumber(row.rowNumber());
            entity.setRowData(JsonColumnMapper.write(row.rowData()));
            entity.setErrors(JsonColumnMapper.write(row.errors()));
            return entity;
        }).toList();
        batchUploadResultRowRepository.saveAll(rows);

        configLockService.release(message.batchId().toString());
        log.debug("Batch validation completion recorded: batchId={}, failedRowCount={}", message.batchId(), rows.size());
    }
}
