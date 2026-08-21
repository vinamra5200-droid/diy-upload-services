package in.qualtechedge.qcp.templates.controller.common;

import in.qualtechedge.qcp.templates.dto.request.LoginRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.LoginResponse;
import in.qualtechedge.qcp.templates.service.keycloak.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/auth")
@RequiredArgsConstructor
@Slf4j
public class UserAuthenticationController {

    private final UserAuthService userAuthService;

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    public ResponseEntity<APIResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        log.info("Login request: user={}, ip={}", loginRequest.username(), request.getRemoteAddr());
        try {
            LoginResponse loginResponse = userAuthService.keycloakUserLogin(loginRequest, request);
            log.info("Login success: user={}, accessToken={}", loginRequest.username(),
                    maskToken(loginResponse.accessToken()));
            return ResponseEntity.ok(APIResponse.success(200, "Login successful", loginResponse));
        } catch (Exception e) {
            log.error("Login failed: user={}, error={}", loginRequest.username(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(APIResponse.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 20) {
            return "***";
        }
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }
}
