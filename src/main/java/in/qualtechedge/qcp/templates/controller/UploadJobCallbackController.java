package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.CallbackCompletedRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.openapi.UploadJobCallbackDocumentation;
import in.qualtechedge.qcp.templates.service.UploadJobCallbackResultService;
import in.qualtechedge.qcp.templates.service.impl.ProcessedResultS3Exporter;
import jakarta.validation.Valid;
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
 * Entry point for consumer-callback-service's per-job completion callback — the reverse leg of
 * {@code PostLoadActionDispatcherImpl}'s Kafka publish, mirroring {@code BatchUploadController}'s
 * validation-completed callback for the outbound-API-delivery leg instead of the validation leg.
 * System-scope (qcp.multitenancy.excluded-paths): the caller has no tenant subdomain to resolve
 * against, so {@code HostContext} is populated by {@code SystemCallbackTenantHeaderFilter} from the
 * trusted {@code X-Tenant-Code} header before this method runs, not inside it — Open Session In View
 * resolves the multi-tenant connection in a {@code HandlerInterceptor} that fires between the filter
 * chain and this method body, so setting it here would already be too late.
 */
@RestController
@RequestMapping("/api/v1/upload-jobs")
@RequiredArgsConstructor
@Slf4j
public class UploadJobCallbackController implements UploadJobCallbackDocumentation {

    private final UploadJobCallbackResultService uploadJobCallbackResultService;
    private final ProcessedResultS3Exporter processedResultS3Exporter;

    @Override
    @PostMapping("/{jobId}/callback-completed")
    public ResponseEntity<APIResponse<Void>> callbackCompleted(@PathVariable String jobId,
            @Valid @RequestBody CallbackCompletedRequest request) {
        log.info("Job callback-completed request: jobId={}, status={}", jobId, request.status());
        // claim() is the idempotency guard: a concurrent duplicate or a timeout-triggered retry of
        // this same callback (consumer-callback-service's retry doesn't know its first attempt is
        // still running) returns false here and skips straight to the completion log below, instead
        // of re-applying the job's status transition a second time.
        if (uploadJobCallbackResultService.claim(request)) {
            try {
                uploadJobCallbackResultService.recordCompletion(request);
            } catch (RuntimeException e) {
                // Reopen the claim so a genuine failure (not a duplicate) stays retryable instead of
                // being silently swallowed as "already handled" on every later retry.
                uploadJobCallbackResultService.unclaim(jobId);
                throw e;
            }
            // Fire-and-forget, off this request thread — see ProcessedResultS3Exporter's javadoc.
            // Runs after recordCompletion so consumer-callback-service's own batch rows (pulled
            // from its DB, not this request body) are already committed on that side.
            processedResultS3Exporter.export(request.tenantCode(), jobId);
        }
        log.info("Job callback completed: jobId={}", jobId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", null));
    }
}
