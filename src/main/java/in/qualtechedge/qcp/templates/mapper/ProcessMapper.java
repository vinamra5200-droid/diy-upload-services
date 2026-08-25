package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import org.springframework.stereotype.Component;

@Component
public class ProcessMapper {

    /** processId is assigned by the caller (ProcessServiceImpl) from process_id_seq — see V1_3_2. */
    public UploadProcess toEntity(ProcessRequest request, String createdBy) {
        UploadProcess entity = new UploadProcess();
        entity.setProcessName(request.processName());
        entity.setDescription(request.description() == null ? "" : request.description());
        entity.setStatus(ConfigStatus.draft);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    public void updateEntity(UploadProcess entity, ProcessRequest request) {
        entity.setProcessName(request.processName());
        entity.setDescription(request.description() == null ? "" : request.description());
    }

    public ProcessResponse toResponse(UploadProcess entity, boolean configLocked) {
        return new ProcessResponse(
                entity.getProcessId(),
                entity.getProcessName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.isValidationsEnabled(),
                entity.getValidationsSkipReason(),
                configLocked,
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
