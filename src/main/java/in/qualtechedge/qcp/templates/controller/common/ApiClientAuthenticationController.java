package in.qualtechedge.qcp.templates.controller.common;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.TokenResponse;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.resolution.HostUtils;
import in.qualtechedge.qcp.templates.properties.KeycloakProperties;
import in.qualtechedge.qcp.templates.service.keycloak.KeycloakAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-clients/auth")
@RequiredArgsConstructor
@Slf4j
public class ApiClientAuthenticationController {

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakProperties keycloakProperties;

    @PostMapping(path = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    public ResponseEntity<APIResponse<TokenResponse>> token(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret,
            HttpServletRequest httpServletRequest) {
        log.info("API client token request: clientId={}, ip={}", clientId, httpServletRequest.getRemoteAddr());

        // Authentication endpoints are excluded from tenant resolution filter,
        // so we need to manually resolve tenant from Host header
        String hostHeader = httpServletRequest.getHeader("Host");
        String currentTenant = HostUtils.extractSubdomain(hostHeader);

        if (currentTenant == null || currentTenant.isBlank()) {
            currentTenant = keycloakProperties.getSuperAdminRealm();
        }

        log.info("Resolved tenant from Host header {}: {}", hostHeader, currentTenant);

        // Set tenant in context for KeycloakAuthService to use
        HostContext.setCurrentTenant(currentTenant);

        try {
            TokenResponse tokenResponse = keycloakAuthService.authenticateClient(clientId, clientSecret, httpServletRequest);
            log.info("API client token generated: clientId={}, accessToken={}",
                    clientId, maskToken(tokenResponse.accessToken()));
            return ResponseEntity.ok(APIResponse.success(200, "Token generated successfully", tokenResponse));
        } catch (Exception e) {
            log.error("API client token failed: clientId={}, error={}", clientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(APIResponse.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
        } finally {
            // Clean up context
            HostContext.clear();
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 20) {
            return "***";
        }
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }
}
