package in.qualtechedge.qcp.templates.config;

import in.qualtechedge.qcp.templates.security.auth.SecurityConstants;
import in.qualtechedge.qcp.templates.security.auth.apikey.ApiKeyAuthenticationFilter;
import in.qualtechedge.qcp.templates.security.auth.apikey.ApiKeyAuthenticationProvider;
import in.qualtechedge.qcp.templates.security.auth.devbypass.DevBypassAuthenticationFilter;
import in.qualtechedge.qcp.templates.security.auth.handler.CustomAuthenticationEntryPoint;
import in.qualtechedge.qcp.templates.security.auth.jwt.AuthTokenFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central Spring Security configuration for the QCP multitenant template.
 * <p>
 * <ul>
 *   <li>Stateless session (Keycloak JWT bearer tokens only — no HTTP sessions).</li>
 *   <li>Tenant-aware JWT validation via {@link TenantAuthenticationManagerResolver}:
 *       the resolver inspects the token's {@code iss} claim to pick the correct
 *       Keycloak realm's JWKS endpoint.</li>
 *   <li>Public endpoints listed in {@link SecurityConstants#PUBLIC_API_PATHS} require
 *       no authentication — everything else is authenticated.</li>
 *   <li>{@link AuthTokenFilter} runs before
 *       {@link UsernamePasswordAuthenticationFilter} to detect Keycloak tokens and
 *       allow the OAuth2 Resource Server to handle them.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;
    private final CustomAuthenticationEntryPoint unauthorizedHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final TenantAuthenticationManagerResolver tenantAuthenticationManagerResolver;
    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    /** LOCAL-DEV-ONLY escape hatch — see {@link DevBypassAuthenticationFilter}. Never true outside
     * {@code application-local.yaml}. */
    @Value("${app.security.dev-bypass-enabled:false}")
    private boolean devBypassEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.debug("Configuring security filter chain with Keycloak OAuth2 Resource Server");

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(SecurityConstants.PUBLIC_API_PATHS).permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(
                    oauth2 -> oauth2.authenticationManagerResolver(tenantAuthenticationManagerResolver));

        log.debug("Adding ApiKeyAuthenticationFilter before UsernamePasswordAuthenticationFilter");
        http.addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyAuthenticationProvider),
                UsernamePasswordAuthenticationFilter.class);

        log.debug("Adding AuthTokenFilter before UsernamePasswordAuthenticationFilter");
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        log.debug("Adding DevBypassAuthenticationFilter before UsernamePasswordAuthenticationFilter (enabled={})",
                devBypassEnabled);
        http.addFilterBefore(new DevBypassAuthenticationFilter(devBypassEnabled),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Placeholder {@link AuthenticationManager} — add a {@code DaoAuthenticationProvider}
     * here when a local {@code UserDetailsService} is implemented.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        log.debug("Building AuthenticationManager with ApiKeyAuthenticationProvider");
        return new ProviderManager(apiKeyAuthenticationProvider);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        log.debug("Configuring JWT authentication converter for Keycloak roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            log.debug("Extracting authorities from JWT for user: {}", jwt.getSubject());

            Set<GrantedAuthority> authorities = new HashSet<>();

            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> roles) {
                    for (Object role : roles) {
                        authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + role.toString()));
                        log.debug("Added realm role: ROLE_{}", role);
                    }
                }
            }

            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
                    Object clientRoles = entry.getValue();
                    if (clientRoles instanceof Map<?, ?> clientMap) {
                        Object roles = clientMap.get("roles");
                        if (roles instanceof List<?> roleList) {
                            for (Object role : roleList) {
                                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_" + role.toString()));
                                log.debug("Added client role: ROLE_{}", role);
                            }
                        }
                    }
                }
            }

            log.debug("Total authorities extracted for {}: {}", jwt.getSubject(), authorities.size());
            return authorities;
        });

        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
}
