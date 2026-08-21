package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ExampleRequest;
import in.qualtechedge.qcp.templates.dto.response.ExampleResponse;
import in.qualtechedge.qcp.templates.entity.ExampleEntity;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.ExampleMapper;
import in.qualtechedge.qcp.templates.repository.ExampleEntityRepository;
import in.qualtechedge.qcp.templates.service.ExampleService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExampleServiceImpl implements ExampleService {

    private final ExampleEntityRepository exampleEntityRepository;
    private final ExampleMapper exampleMapper;

    @Override
    @Transactional
    public ExampleResponse create(ExampleRequest request) {
        log.debug("Creating example with name: {}", request.name());
        ExampleEntity entity = exampleMapper.toEntity(request);
        // saveAndFlush so generated values (timestamps) are populated before mapping the response
        return exampleMapper.toResponse(exampleEntityRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public ExampleResponse getById(UUID id) {
        log.debug("Fetching example with id: {}", id);
        return exampleMapper.toResponse(findEntityOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExampleResponse> getAll() {
        log.debug("Fetching all examples");
        return exampleEntityRepository.findAll().stream()
                .map(exampleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ExampleResponse update(UUID id, ExampleRequest request) {
        log.debug("Updating example with id: {}", id);
        ExampleEntity entity = findEntityOrThrow(id);
        exampleMapper.updateEntity(entity, request);
        return exampleMapper.toResponse(exampleEntityRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.debug("Deleting example with id: {}", id);
        ExampleEntity entity = findEntityOrThrow(id);
        exampleEntityRepository.delete(entity);
    }

    private ExampleEntity findEntityOrThrow(UUID id) {
        return exampleEntityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Example not found with id: " + id));
    }
}
