package in.qualtechedge.qcp.templates.security.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pre-security filter that inspects the Bearer token on every request.
 * <p>
 * When a Keycloak JWT is detected the filter lets it pass through so that the
 * configured OAuth2 Resource Server ({@link in.qualtechedge.qcp.templates.config.TenantAuthenticationManagerResolver})
 * can validate and authenticate it. All other traffic passes through untouched;
 * Spring Security's own {@code authorizeHttpRequests} rules then decide whether
 * the request is allowed.
 * <p>
 * Tenant context for protected endpoints is populated by
 * {@link in.qualtechedge.qcp.templates.multitenancy.resolution.TenantResolutionFilter} (Order 1).
 * For public auth endpoints (excluded from tenant resolution), the caller sets context
 * in the controller via the {@code Host} header.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);

            if (jwt != null) {
                if (isKeycloakToken(jwt)) {
                    log.debug("Detected Keycloak JWT for {}, delegating to OAuth2 Resource Server",
                            request.getRequestURI());
                } else {
                    log.debug("Non-Keycloak token detected for {} — no local JWT store in template",
                            request.getRequestURI());
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            handleException(e, request, response, filterChain);
        }
    }

    /**
     * Extract Bearer token from the {@code Authorization} header.
     */
    public String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    /**
     * Heuristic check: if the JWT payload contains a {@code /realms/} issuer it originated
     * from Keycloak. Validation itself is performed by the OAuth2 Resource Server.
     */
    private boolean isKeycloakToken(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            return payload.contains("/realms/") || payload.contains("keycloak");
        } catch (Exception e) {
            log.debug("Error checking if token is a Keycloak token: {}", e.getMessage());
            return false;
        }
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private void sendJsonError(HttpServletResponse response, String message, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "ERROR");
        errorResponse.put("statusCode", status.value());
        errorResponse.put("errorCode", status.getReasonPhrase());
        errorResponse.put("errorMessage", message);
        errorResponse.put("timestamp", ZonedDateTime.now().toString());
        errorResponse.put("requestId", UUID.randomUUID().toString());

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private void handleException(Exception e, HttpServletRequest request,
                                 HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.error("Cannot set user authentication: {}", e.getMessage(), e);

        if (response.isCommitted()) {
            log.warn("Cannot handle authentication exception — response already committed");
            filterChain.doFilter(request, response);
            return;
        }

        if (isApiRequest(request)) {
            sendJsonError(response, e.getMessage(), HttpStatus.UNAUTHORIZED);
            return;
        }

        switch (e) {
            case ServletException se -> throw se;
            case IOException ioe -> throw ioe;
            default -> throw new ServletException("Authentication failed", e);
        }
    }
}
