package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.CheckerInboxItemResponse;
import in.qualtechedge.qcp.templates.openapi.InboxDocumentation;
import in.qualtechedge.qcp.templates.service.InboxService;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inbox")
@RequiredArgsConstructor
@Slf4j
public class InboxController implements InboxDocumentation {

    private final InboxService inboxService;

    @Override
    @GetMapping
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<List<CheckerInboxItemResponse>>> list() {
        log.info("List checker inbox request");
        List<CheckerInboxItemResponse> response = inboxService.list(CurrentActor.id());
        log.info("Checker inbox retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping("/{changeId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<Object>> accept(@PathVariable String changeId) {
        log.info("Accept inbox item request: changeId={}", changeId);
        Object response = inboxService.accept(changeId);
        log.info("Inbox item accepted: changeId={}", changeId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Accepted", response));
    }

    @Override
    @PostMapping("/{changeId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<Object>> reject(@PathVariable String changeId,
                                                       @Valid @RequestBody RejectRequest request) {
        log.info("Reject inbox item request: changeId={}", changeId);
        Object response = inboxService.reject(changeId, request);
        log.info("Inbox item rejected: changeId={}", changeId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Rejected", response));
    }
}
