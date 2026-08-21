package in.qualtechedge.qcp.templates.multitenancy.resolution;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import in.qualtechedge.qcp.templates.multitenancy.registry.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves the tenant from the {@code Host} header subdomain before any controller code runs
 * (QCC Multi-Tenancy §2.1) and populates {@link HostContext} for datasource routing, MDC and
 * the per-tenant log file.
 * <p>
 * Rules (deny by default):
 * <ul>
 *   <li>Excluded prefixes (actuator, swagger, admin API) run in system scope — no tenant.</li>
 *   <li>Unknown or inactive subdomain → request rejected ({@code Invalid tenant}).</li>
 *   <li>No resolvable subdomain on a tenant-scoped endpoint → request rejected.</li>
 *   <li>Context is always cleared in {@code finally} — no leakage across pooled threads.</li>
 * </ul>
 * QCP standard: tenant resolution lives in a servlet filter (not a {@code @ControllerAdvice})
 * so rejection happens before dispatch and cleanup is guaranteed.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;
    private final MultiTenancyProperties properties;
    /** Jackson 3 mapper — the one Spring Boot 4 auto-configures and MVC itself uses. */
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.excludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Forwarded host first: behind a proxy, Host is the proxy's. Routing a database on
        // the wrong one of the two sends a tenant's queries to the console schema.
        String hostHeader = HostUtils.fromRequest(request);
        String tenantCode = HostUtils.extractSubdomain(hostHeader);

        try {
            HostContext.setCurrentHost(HostUtils.extractDomain(hostHeader));

            if (tenantCode == null) {
                log.warn("Tenant could not be resolved from host '{}' for {}", hostHeader, request.getRequestURI());
                reject(request, response, "Tenant could not be resolved from request host");
                return;
            }

            String normalized = tenantCode.toLowerCase(Locale.ROOT);
            // Registry lookup per request keeps the template simple; add a short-TTL cache if it shows up in profiling
            if (!tenantRepository.existsByShortCodeIgnoreCaseAndStatus(normalized, Tenant.STATUS_ACTIVE)) {
                log.warn("Invalid tenant '{}' from host '{}'", normalized, hostHeader);
                reject(request, response, "Invalid tenant");
                return;
            }

            HostContext.setCurrentTenant(normalized);
            filterChain.doFilter(request, response);
        } finally {
            HostContext.clear();
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        APIResponse<Void> body = APIResponse.<Void>builder()
                .status(APIResponse.Status.ERROR)
                .statusCode(HttpStatus.FORBIDDEN.value())
                .errorCode("QT-TEN-403")
                .errorMessage(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
