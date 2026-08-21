package in.qualtechedge.qcp.templates.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import in.qualtechedge.qcp.templates.dto.request.ExampleRequest;
import in.qualtechedge.qcp.templates.dto.response.ExampleResponse;
import in.qualtechedge.qcp.templates.entity.ExampleEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExampleMapperTest {

    private final ExampleMapper exampleMapper = new ExampleMapper();

    @Test
    void toEntity_copiesRequestFields() {
        ExampleEntity entity = exampleMapper.toEntity(new ExampleRequest("Name", "Description"));

        assertThat(entity.getName()).isEqualTo("Name");
        assertThat(entity.getDescription()).isEqualTo("Description");
        assertThat(entity.getId()).isNull();
    }

    @Test
    void toResponse_copiesEntityFields() {
        ExampleEntity entity = new ExampleEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Name");
        entity.setDescription("Description");

        ExampleResponse response = exampleMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(entity.getId());
        assertThat(response.name()).isEqualTo("Name");
        assertThat(response.description()).isEqualTo("Description");
    }
}
