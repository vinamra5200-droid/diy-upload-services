package in.qualtechedge.qcp.templates.exception;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.multitenancy.provisioning.TenantProvisioningException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler: controllers stay thin and let exceptions propagate here
 * (QCP controller rule). Every exception is converted into the standard APIResponse
 * envelope — clients never see stack traces or internals.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(KeycloakAuthenticationException.class)
    public ResponseEntity<APIResponse<Void>> handleKeycloakAuth(KeycloakAuthenticationException ex,
                                                                HttpServletRequest request) {
        log.warn("Keycloak authentication failed: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .errorCode("QT-AUTH-401")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<APIResponse<Void>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.NOT_FOUND.value())
                .errorCode("QT-RES-404")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<APIResponse<Void>> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.CONFLICT.value())
                .errorCode("QT-RES-409")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ConfigLockedException.class)
    public ResponseEntity<APIResponse<Void>> handleConfigLocked(ConfigLockedException ex, HttpServletRequest request) {
        log.warn("Config locked: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.LOCKED.value())
                .errorCode("QT-CFG-423")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.LOCKED).body(body);
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<APIResponse<Void>> handleBusinessConflict(BusinessConflictException ex,
                                                                     HttpServletRequest request) {
        log.warn("Business conflict: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.CONFLICT.value())
                .errorCode("QT-BIZ-409")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ActorNeSubmitterException.class)
    public ResponseEntity<APIResponse<Void>> handleActorNeSubmitter(ActorNeSubmitterException ex,
                                                                     HttpServletRequest request) {
        log.warn("Actor ne submitter: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.FORBIDDEN.value())
                .errorCode("QT-BIZ-403")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(SubmissionExpiredException.class)
    public ResponseEntity<APIResponse<Void>> handleSubmissionExpired(SubmissionExpiredException ex,
                                                                      HttpServletRequest request) {
        log.warn("Submission expired: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.GONE.value())
                .errorCode("QT-BIZ-410")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<APIResponse<Void>> handleUnprocessable(UnprocessableEntityException ex,
                                                                  HttpServletRequest request) {
        log.warn("Unprocessable entity: {}", ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .errorCode("QT-VAL-422")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.FORBIDDEN.value())
                .errorCode("QT-AUTH-403")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(TenantProvisioningException.class)
    public ResponseEntity<APIResponse<Void>> handleProvisioning(TenantProvisioningException ex,
                                                                HttpServletRequest request) {
        log.error("Tenant provisioning failed on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("QT-TEN-500")
                .errorMessage(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<APIResponse.ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new APIResponse.ErrorDetail(
                        fieldError.getField(), "QT-VAL-010", fieldError.getDefaultMessage()))
                .toList();
        log.warn("Validation failed on {}: {}", request.getRequestURI(), details);
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .errorCode("QT-VAL-001")
                .errorMessage("Validation failed")
                .path(request.getRequestURI())
                .errors(details)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                    HttpServletRequest request) {
        log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .errorCode("QT-VAL-002")
                .errorMessage("Malformed request body")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error processing {}", request.getRequestURI(), ex);
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("QT-SYS-500")
                .errorMessage("An unexpected error occurred")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
