package in.qualtechedge.qcp.templates.security.auth;

/**
 * Security-related constants for the application — the API paths that need no authentication.
 * CORS is configuration, not a constant; see {@link in.qualtechedge.qcp.templates.security.auth.config.CorsProperties}.
 */
public final class SecurityConstants {

    public static final String API_BASE_PATH = "/api/v1";

    /** Public API endpoints — no authentication required. */
    public static final String[] PUBLIC_API_PATHS = {
            API_BASE_PATH + "/auth/**",
            API_BASE_PATH + "/users/auth/**",
            API_BASE_PATH + "/api-clients/auth/**",
            // NOT /admin/tenants/**. It was here, and it made the tenant registry world-readable:
            // an unauthenticated GET returned every tenant with its db_url, and the same matcher
            // covers the POST that provisions a database and a role. Being excluded from *tenant
            // resolution* (qcp.multitenancy.excluded-paths, so it runs in system scope) is not a
            // reason to be excluded from *authentication* — two different questions that happen to
            // share a path prefix. Found on a freshly deployed service, reachable from anything
            // that could reach the edge.
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/api-docs/**",
            API_BASE_PATH + "/ping",
            "/actuator/health",
            "/actuator/prometheus",
            "/actuator/info",
            "/actuator/**",
            "/public/**",
            API_BASE_PATH + "/status",
            API_BASE_PATH + "/public/**"
    };

    // CORS moved to qcp.security.cors.* (see CorsProperties). It lived here as a list of
    // concrete hosts naming one product and one domain, which every project cloned from this
    // template inherited and had to remember to edit — and a missed edit shows up only as an
    // unexplained CORS failure in a browser.

    private SecurityConstants() {
    }
}
