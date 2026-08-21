package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.ExampleRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.ExampleResponse;
import in.qualtechedge.qcp.templates.openapi.ExampleDocumentation;
import in.qualtechedge.qcp.templates.service.ExampleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller (QCP rule): delegates to the service, lets exceptions propagate to the
 * global handler, and implements the OpenAPI contract from the openapi package.
 * Every response is wrapped in the QCP-standard APIResponse envelope.
 */
@RestController
@RequestMapping("/api/v1/examples")
@RequiredArgsConstructor
@Slf4j
public class ExampleController implements ExampleDocumentation {

    private final ExampleService exampleService;

    @Override
    @PostMapping
    public ResponseEntity<APIResponse<ExampleResponse>> create(@Valid @RequestBody ExampleRequest request) {
        log.info("Create example request: name={}", request.name());
        ExampleResponse response = exampleService.create(request);
        log.info("Example created: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Example created", response));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<ExampleResponse>> getById(@PathVariable UUID id) {
        log.info("Get example request: id={}", id);
        ExampleResponse response = exampleService.getById(id);
        log.info("Example retrieved: id={}", id);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping
    public ResponseEntity<APIResponse<List<ExampleResponse>>> getAll() {
        log.info("List examples request");
        List<ExampleResponse> responses = exampleService.getAll();
        log.info("Examples retrieved: count={}", responses.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", responses));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ExampleResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ExampleRequest request) {
        log.info("Update example request: id={}", id);
        ExampleResponse response = exampleService.update(id, request);
        log.info("Example updated: id={}", id);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Example updated", response));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<Void>> delete(@PathVariable UUID id) {
        log.info("Delete example request: id={}", id);
        exampleService.delete(id);
        log.info("Example deleted: id={}", id);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Example deleted", null));
    }
}
