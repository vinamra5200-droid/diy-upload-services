package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.DatabaseConnectionRequest;
import in.qualtechedge.qcp.templates.dto.response.DatabaseConnectionResponse;
import in.qualtechedge.qcp.templates.entity.DatabaseConnection;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnectionMapper {

    public DatabaseConnection toEntity(DatabaseConnectionRequest request, String updatedBy) {
        DatabaseConnection entity = new DatabaseConnection();
        entity.setConnectionId(IdGenerator.generate("db"));
        entity.setProvider(request.provider());
        entity.setConnectionLabel(request.connectionLabel());
        entity.setConnectionRef(request.connectionRef());
        entity.setStatus(ConfigStatus.draft);
        entity.setUpdatedBy(updatedBy);
        entity.getTableNames().addAll(cleanTableNames(request.tableNames()));
        return entity;
    }

    public void updateEntity(DatabaseConnection entity, DatabaseConnectionRequest request, String updatedBy) {
        entity.setProvider(request.provider());
        entity.setConnectionLabel(request.connectionLabel());
        entity.setConnectionRef(request.connectionRef());
        entity.setUpdatedBy(updatedBy);
        entity.getTableNames().clear();
        entity.getTableNames().addAll(cleanTableNames(request.tableNames()));
    }

    public DatabaseConnectionResponse toResponse(DatabaseConnection entity) {
        return new DatabaseConnectionResponse(
                entity.getConnectionId(),
                entity.getProvider(),
                entity.getConnectionLabel(),
                entity.getConnectionRef(),
                List.copyOf(entity.getTableNames()),
                entity.getStatus(),
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt());
    }

    private List<String> cleanTableNames(List<String> tableNames) {
        if (tableNames == null) {
            return List.of();
        }
        return tableNames.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }
}
