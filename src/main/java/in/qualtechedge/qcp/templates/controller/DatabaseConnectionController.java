package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.DatabaseConnectionRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.DatabaseConnectionResponse;
import in.qualtechedge.qcp.templates.openapi.DatabaseConnectionDocumentation;
import in.qualtechedge.qcp.templates.service.DatabaseConnectionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/databases")
@RequiredArgsConstructor
@Slf4j
public class DatabaseConnectionController implements DatabaseConnectionDocumentation {

    private final DatabaseConnectionService databaseConnectionService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<DatabaseConnectionResponse>>> list() {
        log.info("List database connections request");
        List<DatabaseConnectionResponse> response = databaseConnectionService.getAll();
        log.info("Database connections retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{connectionId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<DatabaseConnectionResponse>> getById(@PathVariable String connectionId) {
        log.info("Get database connection request: id={}", connectionId);
        DatabaseConnectionResponse response = databaseConnectionService.getById(connectionId);
        log.info("Database connection retrieved: id={}", connectionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<DatabaseConnectionResponse>> create(@Valid @RequestBody DatabaseConnectionRequest request) {
        log.info("Create database connection request: label={}", request.connectionLabel());
        DatabaseConnectionResponse response = databaseConnectionService.create(request);
        log.info("Database connection created: id={}", response.connectionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Database connection created", response));
    }

    @Override
    @PutMapping("/{connectionId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<DatabaseConnectionResponse>> update(@PathVariable String connectionId,
                                                                          @Valid @RequestBody DatabaseConnectionRequest request) {
        log.info("Update database connection request: id={}", connectionId);
        DatabaseConnectionResponse response = databaseConnectionService.update(connectionId, request);
        log.info("Database connection updated: id={}", connectionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Database connection updated", response));
    }

    @Override
    @PostMapping("/{connectionId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<DatabaseConnectionResponse>> submit(@PathVariable String connectionId) {
        log.info("Submit database connection request: id={}", connectionId);
        DatabaseConnectionResponse response = databaseConnectionService.submit(connectionId);
        log.info("Database connection submitted: id={}", connectionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Database connection submitted", response));
    }

    @Override
    @PostMapping("/{connectionId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<DatabaseConnectionResponse>> accept(@PathVariable String connectionId) {
        log.info("Accept database connection request: id={}", connectionId);
        DatabaseConnectionResponse response = databaseConnectionService.accept(connectionId);
        log.info("Database connection accepted: id={}", connectionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Database connection accepted", response));
    }

    @Override
    @PostMapping("/{connectionId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<DatabaseConnectionResponse>> reject(@PathVariable String connectionId,
                                                                          @Valid @RequestBody RejectRequest request) {
        log.info("Reject database connection request: id={}", connectionId);
        DatabaseConnectionResponse response = databaseConnectionService.reject(connectionId, request);
        log.info("Database connection rejected: id={}", connectionId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Database connection rejected", response));
    }
}
