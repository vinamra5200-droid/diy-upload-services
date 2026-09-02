package in.qualtechedge.qcp.templates.multitenancy.resolution;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import in.qualtechedge.qcp.templates.multitenancy.registry.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
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
 * Resolves the tenant for system-scope completion callbacks from the trusted {@code X-Tenant-Code}
 * header instead of the {@code Host} subdomain — these callers (diy-validation-service,
 * consumer-callback-service) have no tenant subdomain to resolve against ({@link TenantResolutionFilter}
 * excludes both whole prefixes, see {@code qcp.multitenancy.excluded-paths}). One filter for both
 * rather than a near-identical copy per caller — {@link #MATCHERS} is the only thing that grows when
 * a third one is added.
 * <p>
 * Must be a servlet {@code Filter}, not code inside the controller: Spring's Open Session In View
 * opens the Hibernate session — and with it, resolves and locks in the multi-tenant connection — in
 * a {@code HandlerInterceptor.preHandle()}, which runs after the whole filter chain but before the
 * controller method body. Setting {@link HostContext} from inside a controller method was too late:
 * OSIV had already opened the session against whatever tenant {@code HostContext} held at that point
 * (nothing set yet -> {@code system}), and the controller's later call to {@code setCurrentTenant}
 * could no longer change which database that session was bound to — surfaced as {@code relation
 * "upload_attempts" does not exist}, the system DB has no such table. Running as a filter here puts
 * tenant resolution back before OSIV, the same guarantee {@link TenantResolutionFilter} gives every
 * Host-resolved request. The request body's own {@code tenantCode} field stays as the value actually
 * recorded on the audit trail; this header only decides which database the request runs against.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class SystemCallbackTenantHeaderFilter extends OncePerRequestFilter {

    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    /** One entry per system-scope completion callback this filter resolves tenant for. */
    private static final List<PathMatch> MATCHERS = List.of(
            new PathMatch("/api/v1/batch-uploads/", "/validation-completed"),
            new PathMatch("/api/v1/upload-jobs/", "/callback-completed"));

    private final TenantRepository tenantRepository;
    /** Jackson 3 mapper — the one Spring Boot 4 auto-configures and MVC itself uses. */
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return MATCHERS.stream().noneMatch(matcher -> matcher.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantCode = request.getHeader(TENANT_CODE_HEADER);
        try {
            if (tenantCode == null || tenantCode.isBlank()) {
                log.warn("Missing {} header for {}", TENANT_CODE_HEADER, request.getRequestURI());
                reject(request, response, "Missing " + TENANT_CODE_HEADER + " header");
                return;
            }

            String normalized = tenantCode.toLowerCase(Locale.ROOT);
            if (!tenantRepository.existsByShortCodeIgnoreCaseAndStatus(normalized, Tenant.STATUS_ACTIVE)) {
                log.warn("Invalid tenant '{}' on system-scope callback {}", normalized, request.getRequestURI());
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

    private record PathMatch(String prefix, String suffix) {
        boolean matches(String path) {
            return path.startsWith(prefix) && path.endsWith(suffix);
        }
    }
}
