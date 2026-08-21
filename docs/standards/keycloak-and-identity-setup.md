# Keycloak & Identity Setup

QCP services delegate identity to **Keycloak**: users, API clients (service accounts), roles and tokens all live there — services never store passwords and never issue their own JWTs. Each service is an **OAuth2 resource server** that validates Keycloak-issued tokens.

**Reference implementations**:

- **notification-service** (single-tenant) — one realm; resource-server validation, client-credentials tokens for API clients, admin-client provisioning of clients/roles/users.
- **kyc-service** (multi-tenant) — **one realm per tenant**, provisioned automatically during tenant onboarding; realm client secrets stored in Vault.

## Identity Model

| Principal | Keycloak representation | Authenticates with |
|---|---|---|
| **End user** | Realm user | username/password (or SSO) → user token |
| **API client** (service-to-service) | Client with service account | `client_id` + `client_secret` → `client_credentials` token |

Both arrive at the service as a **Bearer JWT**; the resource-server pipeline treats them uniformly and authorization happens on the extracted roles.

## POM Dependencies

```xml
<!-- ==================== Security / Identity ==================== -->
<!-- Spring Security core -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- Spring Boot OAuth2 Resource Server for Keycloak integration -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<!-- Keycloak Admin Client for managing Keycloak clients and roles -->
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>23.0.0</version>
</dependency>
<!-- JAX-RS API dependency for Keycloak admin client -->
<dependency>
    <groupId>jakarta.ws.rs</groupId>
    <artifactId>jakarta.ws.rs-api</artifactId>
</dependency>
```

The admin client is only needed when the service **provisions** Keycloak objects (clients, roles, users); pure resource servers need just the first two.

## Configuration

### Resource server (token validation)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${[SERVICE]_KEYCLOAK_ISSUER_URI}    # https://{host}/realms/{realm}
          jwk-set-uri: ${[SERVICE]_KEYCLOAK_JWK_SET_URI}  # {issuer}/protocol/openid-connect/certs
```

### Service-specific Keycloak properties

```yaml
# Keycloak Configuration
keycloak:
  server-url: ${KEYCLOAK_SERVER_URL}
  realm: ${[SERVICE]_KEYCLOAK_REALM}
  admin-username: ${KEYCLOAK_ADMIN_USERNAME}   # only for services that provision Keycloak objects
  admin-password: ${KEYCLOAK_ADMIN_PASSWORD}
  ssl-skip: false                              # dev-only escape hatch — never true in production
```

Bound by a properties class that also centralizes the endpoint URLs:

```java
@Component
@ConfigurationProperties(prefix = "keycloak")
@Data
public class KeycloakConfig {

    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private String adminUsername;
    private String adminPassword;
    private boolean sslSkip = false;

    public String getIssuerUrl()     { return serverUrl + "/realms/" + realm; }
    public String getTokenEndpoint() { return getIssuerUrl() + "/protocol/openid-connect/token"; }
    public String getLogoutEndpoint(){ return getIssuerUrl() + "/protocol/openid-connect/logout"; }
}
```

**Secrets rule**: `client_secret`, admin credentials and per-tenant realm secrets come from **Vault** (see [Vault & Secrets Setup](vault-and-secrets-setup.md)) — the `${...}` placeholders resolve from the Vault-imported environment, never from committed files.

## Security Filter Chain

Stateless resource server; authorization declared per route; everything else requires authentication (deny by default):

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint unauthorizedHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomJwtDecoder customJwtDecoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Role-protected endpoints
                .requestMatchers("/api/v1/notify/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/api-clients/**").hasRole("ADMIN")
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            // OAuth2 Resource Server configuration
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(customJwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }
}
```

## JWT → Authorities Mapping

Keycloak puts roles in two claims; both are mapped to Spring authorities with the `ROLE_` prefix (so `hasRole("ADMIN")` works), and OAuth scopes become `SCOPE_*`:

```java
// realm_access.roles  → ROLE_{role}        (realm-level roles)
// resource_access.{client}.roles → ROLE_{role}   (client-level roles)
// scope "a b c"       → SCOPE_a, SCOPE_b, SCOPE_c

Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
    roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
}
```

The authentication converter also distinguishes **users from service accounts**: Keycloak names service-account principals `service-account-{client_id}` — the converter detects this and builds the principal from `client_id` (principal type API_CLIENT) instead of `preferred_username` (type USER), so downstream code handles both uniformly.

## Token Validation Hardening

Beyond signature verification (JWK set), the custom decoder enforces:

| Check | Rule |
|---|---|
| **Expiry** | reject expired tokens explicitly |
| **Issuer** | must be this service's realm — a token from another realm/service is invalid here |
| **Audience** | must contain `account` or this service's client — tokens minted for other clients are rejected |

```java
NimbusJwtDecoder delegate = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
// decode() → delegate.decode(token) → validateToken(jwt): expiry + issuer + audience
```

**`ssl-skip`** exists for dev environments with self-signed certificates only — it swaps in a trust-all HTTP client for the JWK fetch and logs a warning. It must be `false` everywhere that matters.

## Service-to-Service Tokens (client_credentials)

API clients never get passwords — they exchange their client credentials for a Keycloak token:

```text
POST /api/v1/auth/token            (the service proxies to Keycloak's token endpoint)
  X-Client-Id:     {client_id}
  X-Client-Secret: {client_secret}
→ { access_token, refresh_token, expires_in, ... }   (Keycloak token response)
```

```java
// grant_type=client_credentials against {issuer}/protocol/openid-connect/token
MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
body.add("grant_type", "client_credentials");
body.add("client_id", keycloakConfig.getClientId());
body.add("client_secret", keycloakConfig.getClientSecret());
```

The caller then sends `Authorization: Bearer {access_token}` on every request; the resource-server pipeline above validates it. In multi-tenant services this is also how the **tenant is resolved for API traffic** — the authenticated client is bound to exactly one tenant (see the QCC Multi-Tenancy page §2.2).

## Provisioning Keycloak Objects (Admin Client)

Services that onboard clients/users programmatically use the official admin client, authenticated as `admin-cli` against the `master` realm:

```java
Keycloak keycloak = KeycloakBuilder.builder()
        .serverUrl(keycloakConfig.getServerUrl())
        .realm("master")
        .clientId("admin-cli")
        .username(keycloakConfig.getAdminUsername())
        .password(keycloakConfig.getAdminPassword())
        .build();

RealmResource realm = keycloak.realm(keycloakConfig.getRealm());
// idempotent: check findByClientId() first, then create
ClientRepresentation client = new ClientRepresentation();
client.setClientId(clientId);
client.setClientAuthenticatorType("client-secret");
client.setSecret(clientSecret);
client.setServiceAccountsEnabled(true);   // enables client_credentials
realm.clients().create(client);
```

**Rules**: provisioning operations are **idempotent** (check-then-create — a re-run must not fail or duplicate); generated client secrets go **straight to Vault**, never to the database or logs.

## Multi-Tenant Services: Realm per Tenant

Multi-tenant services (kyc-service) extend the model: **each tenant gets its own Keycloak realm**, mirroring database-per-tenant isolation at the identity layer.

- Realm creation is **Stage 3 of tenant onboarding** (after database and Vault stages) — `KeycloakTenantService.setupTenantRealm(tenantCode)` creates the realm, its clients and the initial tenant admin, then returns the realm URL.
- The realm URL is stored on the tenant registry row (`keycloak_realm_url`); the realm's client secrets and the tenant-admin bootstrap password are stored **in Vault** under the tenant's path.
- Token validation is then realm-aware: a tenant user's token is only valid against that tenant's realm — cross-tenant tokens fail the issuer check by construction.

| Tenancy | Realms | Reference |
|---|---|---|
| Single-tenant service | 1 realm for the service | notification-service |
| Multi-tenant service | 1 realm **per tenant**, provisioned at onboarding | kyc-service |

## Rules

1. **Keycloak is the only identity source** — services never store passwords, never mint their own JWTs; every service is a stateless OAuth2 resource server.
2. **Deny by default** — explicit `permitAll()` for public paths (actuator, swagger, auth); every other route requires authentication, role-protected routes use `hasRole(...)`.
3. **Map both role claims** — `realm_access` and `resource_access` → `ROLE_*`; scopes → `SCOPE_*`.
4. **Validate beyond the signature** — expiry, issuer (own realm only) and audience are checked explicitly; tokens from another realm or minted for another client are rejected.
5. **Service accounts over shared users** — service-to-service calls use `client_credentials` with a per-service client; the `service-account-*` principal is detected and typed as API_CLIENT.
6. **Keycloak secrets live in Vault** — client secrets, admin credentials, tenant realm secrets; `${...}` placeholders only in YAML.
7. **Admin provisioning is idempotent** and uses the official `keycloak-admin-client` (`admin-cli` on `master`) — never raw REST against the admin API.
8. `ssl-skip` is a dev-only escape hatch for self-signed certs — always `false` outside local/dev, and it logs a warning when active.
9. **Multi-tenant = realm per tenant**, created during onboarding with no manual steps; the registry stores the realm URL, Vault stores its secrets.
