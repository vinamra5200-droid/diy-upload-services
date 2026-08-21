package in.qualtechedge.qcp.templates.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.qualtechedge.qcp.templates.dto.request.ExampleRequest;
import in.qualtechedge.qcp.templates.dto.response.ExampleResponse;
import in.qualtechedge.qcp.templates.entity.ExampleEntity;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.ExampleMapper;
import in.qualtechedge.qcp.templates.repository.ExampleEntityRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExampleServiceImplTest {

    @Mock
    private ExampleEntityRepository exampleEntityRepository;

    private ExampleServiceImpl exampleService;

    @BeforeEach
    void setUp() {
        exampleService = new ExampleServiceImpl(exampleEntityRepository, new ExampleMapper());
    }

    @Test
    void create_savesEntityAndReturnsResponse() {
        ExampleRequest request = new ExampleRequest("Sample", "A sample description");
        when(exampleEntityRepository.saveAndFlush(any(ExampleEntity.class)))
                .thenAnswer(invocation -> {
                    ExampleEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        ExampleResponse response = exampleService.create(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Sample");
        assertThat(response.description()).isEqualTo("A sample description");
        verify(exampleEntityRepository).saveAndFlush(any(ExampleEntity.class));
    }

    @Test
    void getById_whenMissing_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(exampleEntityRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exampleService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void update_whenFound_appliesChanges() {
        UUID id = UUID.randomUUID();
        ExampleEntity existing = new ExampleEntity();
        existing.setId(id);
        existing.setName("Old");
        when(exampleEntityRepository.findById(id)).thenReturn(Optional.of(existing));
        when(exampleEntityRepository.save(any(ExampleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExampleResponse response = exampleService.update(id, new ExampleRequest("New", null));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.id()).isEqualTo(id);
    }
}
