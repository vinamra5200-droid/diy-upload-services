package in.qualtechedge.qcp.templates.security.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS configuration ({@code qcp.security.cors.*}).
 *
 * <p>Origins are expressed as <em>patterns</em>, not a list of hosts. Under the QCP subdomain
 * convention every tenant gets {@code {tenant}-{product}-{env}.{domain}}, so one pattern covers
 * all of them and onboarding a tenant needs no code change — enumerating hosts meant a new
 * tenant's console failed CORS until someone edited a constant and redeployed.
 *
 * <p>Patterns rather than {@code allowed-origins} for a second reason: this API sends
 * credentials, and the CORS spec forbids pairing {@code Access-Control-Allow-Credentials} with a
 * wildcard origin. Spring's {@code allowedOriginPatterns} matches the pattern and then echoes
 * the concrete origin back, which is legal where a bare {@code *} is not.
 *
 * @param allowedOriginPatterns origin patterns, e.g. {@code https://*-myapp-dev.example.com}
 * @param allowedMethods        HTTP methods the browser may use
 * @param allowedHeaders        request headers the browser may send
 * @param allowCredentials      whether cookies and Authorization headers may cross origins
 */
@ConfigurationProperties(prefix = "qcp.security.cors")
public record CorsProperties(
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        Boolean allowCredentials
) {
    public CorsProperties {
        // Localhost only. A template that shipped a working default for a real domain would be
        // a template that quietly allows somebody else's domain in every project cloned from it.
        allowedOriginPatterns = allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()
                ? List.of("http://localhost:*", "https://localhost:*")
                : allowedOriginPatterns;
        allowedMethods = allowedMethods == null || allowedMethods.isEmpty()
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : allowedMethods;
        allowedHeaders = allowedHeaders == null || allowedHeaders.isEmpty()
                ? List.of("*")
                : allowedHeaders;
        allowCredentials = allowCredentials == null || allowCredentials;
    }
}
