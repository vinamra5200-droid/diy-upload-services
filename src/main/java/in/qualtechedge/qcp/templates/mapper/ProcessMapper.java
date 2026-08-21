package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import org.springframework.stereotype.Component;

@Component
public class ProcessMapper {

    public UploadProcess toEntity(ProcessRequest request, String createdBy) {
        UploadProcess entity = new UploadProcess();
        entity.setProcessId(IdGenerator.generate("proc"));
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

    public ProcessResponse toResponse(UploadProcess entity) {
        return new ProcessResponse(
                entity.getProcessId(),
                entity.getProcessName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.isValidationsEnabled(),
                entity.getValidationsSkipReason(),
                entity.isConfigLocked(),
                entity.getConfigLockRef(),
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
