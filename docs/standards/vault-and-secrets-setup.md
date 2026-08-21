# Vault & Secrets Setup

QCP services store secrets in **HashiCorp Vault** — never in Git, never in config files on server environments. This page covers the Vault layout, the two integration patterns used by QCP services, and the rules for handling secrets.

**Reference implementations**:

- **notification-service** (single-tenant) — Pattern A only: all secrets imported at startup via Spring Cloud Vault; no Vault code in the service at all.
- **kyc-service** (multi-tenant) — Pattern A for its own startup secrets **plus** Pattern B, a custom runtime client for per-tenant secrets.

## What Goes in Vault

| Secret type | Examples |
|---|---|
| **Database credentials** | Service DB username/password; per-tenant DB credentials |
| **API client credentials** | `api-client-id` secrets for service-to-service calls |
| **Identity secrets** | Keycloak client secrets, tenant admin bootstrap passwords |
| **Third-party keys** | Provider API keys, signing keys |

**Never in Vault**: non-secret configuration (URLs, feature flags, pool sizes) — that stays in `application-<profile>.yaml` / environment variables.

## Secret Path Convention (KV v2)

All QCP secrets live in a **KV version 2** secrets engine. The path is locked:

```text
{mount-path}/data/{application-name}/{env}/...
```

| Segment | Meaning | Example |
|---|---|---|
| `{mount-path}` | KV v2 engine mount | `secret` |
| `{application-name}` | The service (artifactId) | `kyc-service` |
| `{env}` | `dev` / `uat` / `prod` | `dev` |

Per-tenant secrets (multi-tenant services) extend the path with the tenant short code:

```text
{mount-path}/data/{application-name}/{env}/tenants/{tenant_code}
```

Standard fields stored per tenant: `db_username`, `db_password`, `api_client_secret` (plus identity secrets such as `keycloak_backend_secret` where applicable).

**Rules**:

- KV v2 reads/writes go through `/data/`; deletes go through `/metadata/` (deleting metadata removes all versions).
- The **`local` profile maps to the `dev` environment** in Vault paths — developers read dev secrets, they never get their own tree.
- Tenant codes in paths are always lowercase.

## Authentication: AppRole Only

Services authenticate to Vault with **AppRole** (`role_id` + `secret_id`) — never a root token, never userpass.

```text
POST {vault-url}/v1/auth/approle/login   { "role_id": ..., "secret_id": ... }
→ auth.client_token   (sent as X-Vault-Token on subsequent calls)
```

- `role_id`/`secret_id` are injected as **environment variables** at deployment (`VAULT_ROLE_ID`, `VAULT_SECRET_ID`) — they are the only Vault secrets the platform handles outside Vault.
- The Vault policy bound to the AppRole grants access **only to the service's own subtree** (`{application-name}/{env}/*`).
- **Cache the client token** and renew it shortly before expiry — do not log in per request (see the runtime client below).

## Pattern A — Startup Secrets (Spring Cloud Vault)

Use Spring Cloud Vault when the service needs secrets **at startup** (e.g. the datasource password): Vault values are imported straight into the Spring `Environment`, so `${DB_PASSWORD}`-style placeholders resolve from Vault instead of environment variables.

**This is the only pattern a single-tenant service needs** — notification-service runs entirely on it, with not a single line of Vault code in the service.

### POM Dependencies

```xml
<properties>
    <spring-cloud.version>2025.1.1</spring-cloud.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud BOM — required for the Vault starter version -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- ==================== Secrets Management ==================== -->
    <!-- Spring Cloud Vault: imports Vault secrets into the Environment at startup -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-vault-config</artifactId>
    </dependency>
</dependencies>
```

### Configuration

#### application-dev.yaml / application-uat.yaml / application-prod.yaml

```yaml
spring:
  # Vault Configuration — secrets imported into the Environment at startup
  config:
    import: "vault://"
  cloud:
    vault:
      uri: ${VAULT_ADDR}
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      kv:
        enabled: true
        backend: mws                                  # KV v2 mount path
        default-context: [service-name]/dev           # {application-name}/{env}
        application-name: [service-name]/dev
  datasource:
    url: ${DB_URL}            # ← resolved from the imported Vault document,
    username: ${DB_USERNAME}  #   not from environment variables
    password: ${DB_PASSWORD}
```

The KV context embeds the environment (`[service-name]/dev`, `[service-name]/uat`, …) so each profile file points at its own Vault subtree. Every key in that Vault document becomes a property — the `${DB_USERNAME}` placeholders below it resolve from Vault.

#### application-local.yaml

```yaml
spring:
  cloud:
    vault:
      enabled: false   # local profile: real local-only values in YAML, no Vault dependency
```

**Note**: with `spring.config.import: "vault://"` the service **fails fast** when Vault is unreachable — a service must not start with missing secrets. Keep the import line out of `application.yaml` (base) so the local profile is not affected.

## Pattern B — Runtime Secrets (Custom KV v2 Client)

Use a custom client when secrets are needed **per request / per tenant at runtime** — e.g. fetching a tenant's DB credentials while building its connection pool, or writing a freshly generated secret during tenant onboarding. Spring Cloud Vault cannot do this (it binds at startup).

### Properties Class

```java
@Component
@ConfigurationProperties(prefix = "tenant-vault")
@Data
public class TenantVaultProperties {

    private boolean enabled;
    private String url;
    private String authentication;       // APPROLE
    private TenantAppRole tenantAppRole;
    private String mountPath;            // KV v2 mount
    private String applicationName;      // [service-name]

    @Data
    public static class TenantAppRole {
        private String roleId;
        private String secretId;
    }
}
```

### Configuration

```yaml
# Custom vault configuration (runtime per-tenant secrets)
tenant-vault:
  enabled: ${VAULT_ENABLED:true}
  url: ${VAULT_ADDR}
  authentication: APPROLE
  tenant-app-role:
    role-id: ${VAULT_TENANT_ROLE_ID}
    secret-id: ${VAULT_TENANT_SECRET_ID}
  mount-path: ${VAULT_BACKEND}
  application-name: ${VAULT_APPLICATION_NAME}
```

### Service: AppRole Login with Token Caching

The runtime client logs in once and caches the token, renewing it shortly before expiry (double-checked locking — concurrent callers never trigger parallel logins):

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class VaultCredentialService {

    private final RestTemplate restTemplate;
    private final TenantVaultProperties vaultProperties;

    /** Cached Vault client token. */
    private volatile String cachedToken;
    /** Expiry instant for the cached token (renewed before this time). */
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    /** Renewal window: refresh the token this many seconds before expiry. */
    private static final long TOKEN_RENEW_BEFORE_EXPIRY_SECONDS = 300;
    /** Default token TTL assumed when Vault does not report one (55 min). */
    private static final long DEFAULT_TOKEN_TTL_SECONDS = 3300;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private String getOrRefreshToken() {
        if (!vaultProperties.isEnabled()) {
            throw new VaultException("Vault is disabled. Enable vault in application configuration.");
        }
        if (Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        tokenLock.lock();
        try {
            // Double-checked locking: another thread may have refreshed while we waited
            if (Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }
            cachedToken = loginWithAppRole();
            tokenExpiresAt = Instant.now()
                    .plusSeconds(DEFAULT_TOKEN_TTL_SECONDS - TOKEN_RENEW_BEFORE_EXPIRY_SECONDS);
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }
}
```

### Reading and Writing Secrets (KV v2)

```java
private String buildSecretDataUrl(String tenantCode) {
    return String.format("%s/v1/%s/data/%s/%s/tenants/%s",
            vaultProperties.getUrl(), vaultProperties.getMountPath(),
            vaultProperties.getApplicationName(), getVaultEnvironment(), tenantCode.toLowerCase());
}

// READ: GET {data-url} with X-Vault-Token header → body.data.data is the secret map
// WRITE: POST {data-url} with body { "data": { "db_username": ..., "db_password": ... } }
// PARTIAL UPDATE: read existing map, merge new fields, POST the merged map
//                 (KV v2 versions the whole document — a plain POST replaces it)
// DELETE: DELETE {metadata-url} (the /metadata/ path removes all versions)

private String getVaultEnvironment() {
    // local developers read the dev tree — there is no "local" environment in Vault
    return "local".equalsIgnoreCase(environment) ? "dev" : environment;
}
```

### Error Handling

Map Vault HTTP errors to meaningful exceptions — never let a raw `RestTemplate` exception escape:

| Vault response | Handling |
|---|---|
| `401 Unauthorized` on login | `VaultException("Invalid AppRole credentials")` |
| `403 Forbidden` | `VaultException("Insufficient permissions")` — policy does not cover the path |
| `404 Not Found` on read | Secret does not exist — `ResourceNotFoundException` (or empty, by use case) |
| Connection failure | `VaultException("Cannot connect to Vault")` |
| `enabled: false` but called | Fail fast with a clear message — never silently skip |

## Choosing a Pattern

| Need | Pattern | Reference |
|---|---|---|
| **Single-tenant service** — DB password, API keys known at startup | **A — Spring Cloud Vault** (`vault://` import) only | notification-service |
| Per-tenant credentials resolved/written at runtime | **B — custom KV v2 client** | kyc-service |
| **Multi-tenant service** (typical) | Both, with **separate AppRoles** (startup role: read-only on the service tree; tenant role: read/write on `…/tenants/*`) | kyc-service |

In multi-tenant services, put pattern B behind a **credential-provider interface** (see the QCC Multi-Tenancy page) so local development can run a config-based implementation while server profiles use Vault — routing and provisioning code never knows the difference.

## Rules

1. **Secrets never live in Git or server config files** — Vault is the source of truth; only `VAULT_ADDR` / `VAULT_ROLE_ID` / `VAULT_SECRET_ID` are injected as environment variables.
2. **AppRole authentication only** — no root tokens, no long-lived static tokens; one AppRole per service, policy scoped to the service's own subtree.
3. **Cache the client token** and renew it before expiry (5-minute window) — never log in per request.
4. **Never log secret values** — log tenant codes, paths and outcomes only (`[Vault] DB credentials stored for tenant: {}`).
5. **KV v2 path discipline** — `/data/` for read/write, `/metadata/` for delete; partial updates are read-merge-write.
6. `local` profile: Vault disabled (`spring.cloud.vault.enabled: false`); when local code must reach Vault, it reads the **dev** tree.
7. **Fail fast** — a service (or tenant pool) must not come up with missing secrets; disabled-but-called is an error, not a no-op.
8. The tenant registry (system DB) stores connection **URLs only** — credentials exist exclusively in Vault.
9. **Don't hand-roll Vault clients in single-tenant services** — Pattern A covers them with zero code. A custom client (Pattern B) exists solely for runtime per-tenant secrets in multi-tenant services.
