package in.qualtechedge.qcp.templates.security.auth.devbypass;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * LOCAL-DEV-ONLY escape hatch: fabricates an authenticated {@link JwtAuthenticationToken}
 * so the maker-checker admin API can be exercised without a running Keycloak.
 * <p>
 * Gated by {@code app.security.dev-bypass-enabled} (default {@code false}). It is only ever
 * set {@code true} in {@code application-local.yaml} — never add it to a server profile. When
 * disabled this filter is a pure no-op with no per-request cost beyond the boolean check.
 * <p>
 * Only kicks in when the request carries no {@code Authorization} header at all, so a real
 * Bearer token (once Keycloak is wired back up) always takes the normal OAuth2 Resource Server
 * path untouched. The fabricated {@link Jwt} carries an {@code actorId} claim (read by
 * {@link in.qualtechedge.qcp.templates.utils.CurrentActor}) and a {@code ROLE_*} authority
 * (read by {@code @PreAuthorize("hasRole(...)")}), taken from the {@code X-Dev-Actor-Id} /
 * {@code X-Dev-Actor-Role} request headers so a caller can act as either maker or checker.
 */
@Slf4j
public class DevBypassAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACTOR_ID_HEADER = "X-Dev-Actor-Id";
    public static final String ACTOR_ROLE_HEADER = "X-Dev-Actor-Role";

    private static final String DEFAULT_ACTOR_ID = "maker_admin_01";
    private static final String DEFAULT_ROLE = "makerAdmin";

    private final boolean enabled;

    public DevBypassAuthenticationFilter(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            log.warn("SECURITY: DevBypassAuthenticationFilter is ENABLED — every unauthenticated request "
                    + "will be treated as a trusted admin actor. This must never be true outside local dev.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || StringUtils.hasText(request.getHeader("Authorization"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String actorId = firstNonBlank(request.getHeader(ACTOR_ID_HEADER), DEFAULT_ACTOR_ID);
        String role = firstNonBlank(request.getHeader(ACTOR_ROLE_HEADER), DEFAULT_ROLE);

        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("dev-bypass")
                .header("alg", "none")
                .claim("sub", actorId)
                .claim("actorId", actorId)
                .claim("preferred_username", actorId)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)), actorId);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.warn("DEV AUTH BYPASS: {} {} authenticated as actorId={} role={} (no Keycloak token presented)",
                request.getMethod(), request.getRequestURI(), actorId, role);

        filterChain.doFilter(request, response);
    }

    private static String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
