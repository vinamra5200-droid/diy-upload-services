package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.UploadRoleRequest;
import in.qualtechedge.qcp.templates.dto.response.UploadRoleResponse;
import in.qualtechedge.qcp.templates.entity.UploadRole;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UploadRoleMapper {

    public UploadRole toEntity(UploadRoleRequest request, String createdBy) {
        UploadRole entity = new UploadRole();
        entity.setRoleId(IdGenerator.generate("role"));
        entity.setRoleName(request.roleName());
        entity.setDescription(request.description() == null ? "" : request.description());
        entity.setActive(Boolean.TRUE.equals(request.isActive()));
        entity.setStatus(ConfigStatus.draft);
        entity.setCreatedBy(createdBy);
        entity.getProcessAccess().addAll(request.processAccess() == null ? List.of() : request.processAccess());
        return entity;
    }

    public void updateEntity(UploadRole entity, UploadRoleRequest request) {
        entity.setRoleName(request.roleName());
        entity.setDescription(request.description() == null ? "" : request.description());
        entity.setActive(Boolean.TRUE.equals(request.isActive()));
        entity.getProcessAccess().clear();
        entity.getProcessAccess().addAll(request.processAccess() == null ? List.of() : request.processAccess());
    }

    public UploadRoleResponse toResponse(UploadRole entity) {
        return new UploadRoleResponse(
                entity.getRoleId(),
                entity.getRoleName(),
                entity.getDescription(),
                new ArrayList<>(entity.getProcessAccess()),
                entity.isActive(),
                entity.getStatus(),
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
