# java-springboot-multitenant-template

QCP **multitenant** Spring Boot service template — **database-per-tenant** isolation implementing the
[QCC Multi-Tenancy standard](docs/standards/multi-tenancy.md) (reference implementation: kyc-service).
Built on top of `java-springboot-template`; everything from the base template (APIResponse envelope,
logging standards, profiles, Docker, Flyway conventions) applies here too.

**Stack:** Spring Boot 4.x · Java 25 · Maven · PostgreSQL **17.6** · Flyway · Hibernate multi-tenancy

> **Starting a new project?** One command, and the result runs — it renames everything and
> fills in the values a service cannot start without (product segment, tenant registry and
> credentials, CORS patterns, seed addresses):
>
> ```bash
> script/rename-project.sh --package in.qualtechedge.billing \
>                         --artifact billing-service --db-prefix billing
> ```
>
> Run it on a fresh clone before writing any code — it rewrites every source file, and it
> refuses to finish if anything still names the template or if a required value is left blank.
> `--tenants`, `--db-port`, `--platform-tenant`/`--realm` and `--drop-example` cover what you
> already know; **[NEW-PROJECT.md](NEW-PROJECT.md)** explains what is left to decide.

## What it demonstrates

| Capability | How |
|---|---|
| **Tenant resolution** | `Host` header subdomain `{tenant}-{product}-{env}.qualtechedge.in` → validated against the registry in a servlet filter; unknown/missing tenant → `403` (deny by default) |
| **Tenant context** | `HostContext` (ThreadLocal) + MDC `tenant`/`host`; cleared in `finally` |
| **Data isolation** | One PostgreSQL **database per tenant**, owned by its own DB role; Hibernate `CurrentTenantIdentifierResolver` + `MultiTenantConnectionProvider` route to per-tenant HikariCP pools; no fallback, no cross-tenant code path; `REVOKE CONNECT … FROM PUBLIC` |
| **System (superadmin) DB** | Holds the **tenant registry** (`tenant.tenants`: `short_code`, `db_url`, `status`) — never tenant business data |
| **Runtime provisioning** | New tenant = registry row + credentials → role + `CREATE DATABASE` + per-tenant Flyway + pool — **no restart** (startup initializer + admin API + lazy pickup) |
| **Per-tenant logging** | Every line carries `tenant=` (MDC); a SiftingAppender writes a **separate log file per tenant** (`logs/mt-template-<tenant>.log`) |
| **Identity** | Either a Keycloak of this service's own (realm per tenant, separated by issuer) or **identity-portal-service** (one realm per platform tenant, separated by an audience derived per host). One property decides which — [`RUN-WITH-IDENTITY-PORTAL.md`](RUN-WITH-IDENTITY-PORTAL.md) |

v1 scope: tenant DB credentials come from configuration (`qcp.multitenancy.tenants.*`) behind the
`TenantCredentialProvider` interface — the Vault (AppRole) implementation slots in later without touching
routing/provisioning code.

## Architecture

```text
request ── Host: client1-app-dev.qualtechedge.in
   │
   ▼
TenantResolutionFilter ──── registry lookup ────────┐
   │  HostContext = client1 (ThreadLocal + MDC)     │
   ▼                                                ▼
Controller → Service → Repository          ┌─────────────────┐
   │                                       │   system DB     │
   ▼                                       │ tenant.tenants  │
Hibernate                                  │ (superadmin)    │
   TenantIdentifierResolver ── "client1"   └─────────────────┘
   TenantConnectionProvider ── pool lookup
   │
   ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ client1 DB   │  │ client2 DB   │  │ clientN DB   │   ← created at runtime,
│ (own role,   │  │ (own role,   │  │ (own role,   │     own role owns the DB,
│  own pool)   │  │  own pool)   │  │  own pool)   │     PUBLIC connect revoked
└──────────────┘  └──────────────┘  └──────────────┘
```

- `multitenancy/context` — `HostContext` (ThreadLocal tenant + host, mirrored into MDC)
- `multitenancy/resolution` — `TenantResolutionFilter` (subdomain → registry check → context), `HostUtils`
- `multitenancy/registry` — `Tenant` entity + repository (`tenant.tenants` in the system DB)
- `multitenancy/routing` — Hibernate `TenantIdentifierResolver`, `TenantConnectionProvider`, `MultiTenantJpaConfig`
- `multitenancy/datasource` — `TenantDataSourceManager` (per-tenant HikariCP pools, lazy registration)
- `multitenancy/credentials` — `TenantCredentialProvider` (interface) + config-based v1 implementation
- `multitenancy/provisioning` — `TenantProvisioningService` (role + DB + Flyway + pool), startup initializer

Flyway is split: `db/migration` migrates the **system DB** (registry + seeds, run by Spring Boot), `db/tenant`
migrates **every tenant DB** (run per tenant by the provisioning service as the tenant's own role).

## Quick start (local)

Prereqs: Java 25, Maven, PostgreSQL **15+** on `localhost:5432` with superuser `postgres`/`postgres`
(the system datasource user needs `CREATEDB` + `CREATEROLE`).

```bash
# 1. Create the system database (the only one created by hand — ever)
psql -U postgres -c 'CREATE DATABASE "diy-batch-upload-db";'

# 2. Run — Flyway migrates the system DB, then the startup initializer provisions
#    qc / client1 / client2: role + database + per-tenant Flyway + pool
mvn spring-boot:run
```

### Exercise tenant isolation

```bash
# Same endpoint, different Host header → physically different databases
curl -H "Host: client1-app-local.qualtechedge.in" http://localhost:8080/api/v1/examples
curl -H "Host: client2-app-local.qualtechedge.in" http://localhost:8080/api/v1/examples

# Unknown tenant → 403 Invalid tenant (deny by default)
curl -H "Host: ghost-app-local.qualtechedge.in" http://localhost:8080/api/v1/examples

# No tenant subdomain on a tenant-scoped endpoint → 403
curl http://localhost:8080/api/v1/examples
```

### Onboard a tenant live (no restart)

`client3` has credentials configured but no registry row — onboard it while the app runs:

```bash
curl -X POST http://localhost:8080/api/v1/admin/tenants \
  -H "Content-Type: application/json" \
  -d '{"name":"Client Three","shortCode":"client3","description":"Onboarded live"}'

# Live seconds later — database created, migrated, pooled:
curl -H "Host: client3-app-local.qualtechedge.in" http://localhost:8080/api/v1/examples
```

### See the isolation in PostgreSQL

```bash
psql -U postgres -c "SELECT datname, pg_get_userbyid(datdba) FROM pg_database WHERE datname LIKE 'mt-template%';"
# each tenant DB is owned by its own role; and:
PGPASSWORD=client1_pass psql -U client1_user -d mt-template-client2-db -c "SELECT 1"
# → FATAL: permission denied for database — tenants cannot even connect to each other's DBs
```

### Per-tenant log files

```text
logs/mt-template-system.log    ← startup, admin API, actuator
logs/mt-template-client1.log   ← only client1 traffic
logs/mt-template-client2.log   ← only client2 traffic
```

## Docker

```bash
docker compose up --build
# app on 9942, dev profile, postgres 17.6 (same pin as the servers); tenant creds as env vars
```

## Endpoints

| Method | Path | Scope |
|---|---|---|
| `GET/POST/PUT/DELETE` | `/api/v1/examples[/{id}]` | **Tenant** — requires tenant subdomain |
| `GET` | `/api/v1/admin/tenants` | System — list registry |
| `POST` | `/api/v1/admin/tenants` | System — onboard tenant (no restart) |
| `GET` | `/actuator/health` · `/actuator/prometheus` · `/swagger-ui.html` | System |

## Rules recap (multitenant additions to the base template)

- Tenant identity comes only from the validated subdomain (or, later, the authenticated API client) — never from a request body/query param.
- **Database-per-tenant is the platform standard**; no shared schema, no `tenant_id` columns, no schema-per-tenant.
- An unknown tenant identifier fails hard — never add a fallback datasource.
- `ddl-auto` stays `none`: one EMF maps system + tenant entities; Flyway owns both schemas.
- Tenant migrations live in `db/tenant` and must work on every tenant DB (single `public` schema, `gen_random_uuid()`).
- Tenant context is cleared at request end and copied explicitly across async boundaries.
- Credentials: `TenantCredentialProvider` only — registry stores `db_url`, never credentials. Config-based v1; Vault later.

See [`AGENTS.md`](AGENTS.md) and [`docs/standards/`](docs/standards/) for the full rule set.
