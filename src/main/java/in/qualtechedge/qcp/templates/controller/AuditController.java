package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.AuditEventResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.openapi.AuditDocumentation;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController implements AuditDocumentation {

    private final AuditEventService auditEventService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<PageResponse<AuditEventResponse>>> list(
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("List audit events request: processId={}, actorId={}, eventCode={}", processId, actorId, eventCode);
        PageResponse<AuditEventResponse> response = auditEventService.list(processId, actorId, eventCode, outcome, from, to, page, limit);
        log.info("Audit events retrieved: count={}", response.content().size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        log.info("Export audit events request: processId={}, actorId={}, eventCode={}", processId, actorId, eventCode);
        // CSV download breaks out of the APIResponse envelope by design — the contract requires a
        // raw text/csv body (admin-api-contract.md §9.2), not JSON wrapping a CSV string.
        String csv = auditEventService.exportCsv(processId, actorId, eventCode, outcome, from, to);
        log.info("Audit events exported: bytes={}", csv.length());
        return ResponseEntity.ok().contentType(MediaType.valueOf("text/csv")).body(csv);
    }
}
