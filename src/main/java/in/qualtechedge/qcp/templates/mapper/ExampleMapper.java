package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.ExampleRequest;
import in.qualtechedge.qcp.templates.dto.response.ExampleResponse;
import in.qualtechedge.qcp.templates.entity.ExampleEntity;
import org.springframework.stereotype.Component;

/**
 * Manual DTO ⇆ entity converter (QCP mapper rule: mapping only, no business logic).
 */
@Component
public class ExampleMapper {

    public ExampleEntity toEntity(ExampleRequest request) {
        ExampleEntity entity = new ExampleEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return entity;
    }

    public void updateEntity(ExampleEntity entity, ExampleRequest request) {
        entity.setName(request.name());
        entity.setDescription(request.description());
    }

    public ExampleResponse toResponse(ExampleEntity entity) {
        return new ExampleResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
