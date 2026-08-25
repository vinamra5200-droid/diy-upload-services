package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.BatchValidationCompletedRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.openapi.BatchUploadDocumentation;
import in.qualtechedge.qcp.templates.service.BatchValidationResultService;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import in.qualtechedge.qcp.templates.service.impl.ValidatedResultS3Exporter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entry point for diy-validation-service's per-batch completion callback — the reverse leg of
 * {@code BatchChunkPublisher}'s Kafka publish, replacing the Kafka-based
 * {@code BatchValidationCompletedListener} this endpoint used to be. System-scope
 * (qcp.multitenancy.excluded-paths): the caller has no tenant subdomain to resolve against, so
 * {@link HostContext} is populated by {@link in.qualtechedge.qcp.templates.multitenancy.resolution.ValidationCompletedTenantHeaderFilter}
 * from the trusted {@code X-Tenant-Code} header before this method runs, not inside it — Open
 * Session In View resolves the multi-tenant connection in a {@code HandlerInterceptor} that fires
 * between the filter chain and this method body, so setting it here would already be too late.
 */
@RestController
@RequestMapping("/api/v1/batch-uploads")
@RequiredArgsConstructor
@Slf4j
public class BatchUploadController implements BatchUploadDocumentation {

    private final ValidationServiceResultsClient validationServiceResultsClient;
    private final BatchValidationResultService batchValidationResultService;
    private final ValidatedResultS3Exporter validatedResultS3Exporter;

    @Override
    @PostMapping("/{batchId}/validation-completed")
    public ResponseEntity<APIResponse<Void>> validationCompleted(@PathVariable UUID batchId,
            @Valid @RequestBody BatchValidationCompletedRequest request) {
        log.info("Batch validation completed request: batchId={}, status={}", batchId, request.status());
        List<ValidationServiceRowsResponse.Row> rows =
                validationServiceResultsClient.fetchAllRows(batchId, request.tenantCode());
        batchValidationResultService.recordCompletion(request, rows);
        // Fire-and-forget, off this request thread — see ValidatedResultS3Exporter's javadoc.
        // Runs after recordCompletion so the rows it reads are already committed.
        validatedResultS3Exporter.export(request.tenantCode(), batchId);
        log.info("Batch validation completed: batchId={}", batchId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", null));
    }
}
