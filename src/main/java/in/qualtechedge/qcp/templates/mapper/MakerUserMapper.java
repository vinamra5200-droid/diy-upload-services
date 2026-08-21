package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.MakerUserRequest;
import in.qualtechedge.qcp.templates.dto.response.MakerUserResponse;
import in.qualtechedge.qcp.templates.entity.MakerUser;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MakerUserMapper {

    public MakerUser toEntity(MakerUserRequest request, String createdBy) {
        MakerUser entity = new MakerUser();
        entity.setUserId(IdGenerator.generate("user"));
        entity.setUsername(request.username());
        entity.setFullName(request.fullName());
        entity.setActive(Boolean.TRUE.equals(request.isActive()));
        entity.setStatus(ConfigStatus.draft);
        entity.setCreatedBy(createdBy);
        entity.getRoleIds().addAll(request.roleIds() == null ? List.of() : request.roleIds());
        return entity;
    }

    public void updateEntity(MakerUser entity, MakerUserRequest request) {
        entity.setUsername(request.username());
        entity.setFullName(request.fullName());
        entity.setActive(Boolean.TRUE.equals(request.isActive()));
        entity.getRoleIds().clear();
        entity.getRoleIds().addAll(request.roleIds() == null ? List.of() : request.roleIds());
    }

    public MakerUserResponse toResponse(MakerUser entity) {
        return new MakerUserResponse(
                entity.getUserId(),
                entity.getUsername(),
                entity.getFullName(),
                new ArrayList<>(entity.getRoleIds()),
                entity.isActive(),
                entity.getStatus(),
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
