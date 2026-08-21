package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.MakerUserRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.MakerUserResponse;
import in.qualtechedge.qcp.templates.openapi.MakerUserDocumentation;
import in.qualtechedge.qcp.templates.service.MakerUserService;
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
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
public class MakerUserController implements MakerUserDocumentation {

    private final MakerUserService makerUserService;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<List<MakerUserResponse>>> list() {
        log.info("List maker users request");
        List<MakerUserResponse> response = makerUserService.getAll();
        log.info("Maker users retrieved: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('makerAdmin', 'checkerAdmin')")
    public ResponseEntity<APIResponse<MakerUserResponse>> getById(@PathVariable String userId) {
        log.info("Get maker user request: id={}", userId);
        MakerUserResponse response = makerUserService.getById(userId);
        log.info("Maker user retrieved: id={}", userId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<MakerUserResponse>> create(@Valid @RequestBody MakerUserRequest request) {
        log.info("Create maker user request: username={}", request.username());
        MakerUserResponse response = makerUserService.create(request);
        log.info("Maker user created: id={}", response.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.success(HttpStatus.CREATED.value(), "Maker user created", response));
    }

    @Override
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<MakerUserResponse>> update(@PathVariable String userId,
                                                                  @Valid @RequestBody MakerUserRequest request) {
        log.info("Update maker user request: id={}", userId);
        MakerUserResponse response = makerUserService.update(userId, request);
        log.info("Maker user updated: id={}", userId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Maker user updated", response));
    }

    @Override
    @PostMapping("/{userId}/submit")
    @PreAuthorize("hasRole('makerAdmin')")
    public ResponseEntity<APIResponse<MakerUserResponse>> submit(@PathVariable String userId) {
        log.info("Submit maker user request: id={}", userId);
        MakerUserResponse response = makerUserService.submit(userId);
        log.info("Maker user submitted: id={}", userId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Maker user submitted", response));
    }

    @Override
    @PostMapping("/{userId}/accept")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<MakerUserResponse>> accept(@PathVariable String userId) {
        log.info("Accept maker user request: id={}", userId);
        MakerUserResponse response = makerUserService.accept(userId);
        log.info("Maker user accepted: id={}", userId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Maker user accepted", response));
    }

    @Override
    @PostMapping("/{userId}/reject")
    @PreAuthorize("hasRole('checkerAdmin')")
    public ResponseEntity<APIResponse<MakerUserResponse>> reject(@PathVariable String userId,
                                                                  @Valid @RequestBody RejectRequest request) {
        log.info("Reject maker user request: id={}", userId);
        MakerUserResponse response = makerUserService.reject(userId, request);
        log.info("Maker user rejected: id={}", userId);
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "Maker user rejected", response));
    }
}
