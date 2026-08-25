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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * LOCAL-DEV-ONLY escape hatch: fabricates an authenticated {@link JwtAuthenticationToken}
 * so the maker-checker admin API, and the upload-operator/checker-operator API, can be exercised
 * without a running Keycloak.
 * <p>
 * Gated by {@code app.security.dev-bypass-enabled} (default {@code false}). It is only ever
 * set {@code true} in {@code application-local.yaml} — never add it to a server profile. When
 * disabled this filter is a pure no-op with no per-request cost beyond the boolean check.
 * <p>
 * Only kicks in when the request carries no {@code Authorization} header at all, so a real
 * Bearer token (once Keycloak is wired back up) always takes the normal OAuth2 Resource Server
 * path untouched. The fabricated {@link Jwt} carries an {@code actorId} claim (read by
 * {@link in.qualtechedge.qcp.templates.utils.CurrentActor}) from the {@code X-Dev-Actor-Id}
 * request header, and {@code ROLE_*} authorities (read by every controller's
 * {@code @PreAuthorize("hasRole(...)")}) for every role gated anywhere in this service —
 * {@link #UNIVERSAL_ROLES} — so one dev actor clears the admin (makerAdmin/checkerAdmin) and
 * upload-operator (makerBatchUpload/checkerBatchUpload) flows alike.
 * <p>
 * {@code X-Dev-Actor-Role}, if a caller still sends it (e.g. a stale header from the UI or an old
 * script), is deliberately ignored — this filter always grants the full role set regardless, so
 * no client-supplied header can ever narrow authorization back down and reintroduce the 403s this
 * was built to eliminate. Four-eyes / ownership checks (e.g. {@code ACTOR_NE_SUBMITTER}) still
 * apply as normal — they compare {@code actorId}, not the authorities granted here.
 */
@Slf4j
public class DevBypassAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACTOR_ID_HEADER = "X-Dev-Actor-Id";

    private static final String DEFAULT_ACTOR_ID = "maker_admin_01";

    /** Every role any controller in this service gates behind {@code @PreAuthorize} — kept in one
     * place here rather than derived reflectively, so it stays an explicit, auditable list. */
    private static final List<String> UNIVERSAL_ROLES =
            List.of("makerAdmin", "checkerAdmin", "makerBatchUpload", "checkerBatchUpload");

    private final boolean enabled;

    public DevBypassAuthenticationFilter(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            log.warn("SECURITY: DevBypassAuthenticationFilter is ENABLED — every unauthenticated request "
                    + "will be treated as a trusted actor holding every role ({}). This must never be true "
                    + "outside local dev.", UNIVERSAL_ROLES);
        }
    }

    /**
     * {@code SessionCreationPolicy.STATELESS} (SecurityConfig) means nothing persists the
     * {@link SecurityContextHolder} context between dispatches, and the SSE upload-events endpoint
     * (MakerUploadController#subscribe) completes via the servlet container's internal
     * {@code ASYNC} dispatch — a second pass through this filter chain. {@link OncePerRequestFilter}
     * skips that pass by default, so without this override the completion dispatch runs with no
     * authentication at all and {@code .anyRequest().authenticated()} denies it, even though the
     * original {@code REQUEST} dispatch that opened the SSE connection was already authenticated.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || StringUtils.hasText(request.getHeader("Authorization"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String actorId = firstNonBlank(request.getHeader(ACTOR_ID_HEADER), DEFAULT_ACTOR_ID);
        // X-Dev-Actor-Role is intentionally not read here — see the class doc. Every dev-bypass
        // request gets every role, no matter what the client sends or omits.
        List<String> roles = UNIVERSAL_ROLES;

        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("dev-bypass")
                .header("alg", "none")
                .claim("sub", actorId)
                .claim("actorId", actorId)
                .claim("preferred_username", actorId)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities, actorId);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.warn("DEV AUTH BYPASS: {} {} authenticated as actorId={} roles={} (no Keycloak token presented)",
                request.getMethod(), request.getRequestURI(), actorId, roles);

        filterChain.doFilter(request, response);
    }

    private static String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
