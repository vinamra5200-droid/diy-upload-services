package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.ExampleRequest;
import in.qualtechedge.qcp.templates.dto.response.ExampleResponse;
import java.util.List;
import java.util.UUID;

/**
 * Service interface defining the contract (QCP service rule);
 * the implementation lives in service/impl.
 */
public interface ExampleService {

    ExampleResponse create(ExampleRequest request);

    ExampleResponse getById(UUID id);

    List<ExampleResponse> getAll();

    ExampleResponse update(UUID id, ExampleRequest request);

    void delete(UUID id);
}
