# AI Agent Context — java-springboot-multitenant-template

This is the **QCP (Qualtech Core Platform) multitenant Spring Boot service template** — the base
`java-springboot-template` plus **database-per-tenant** multi-tenancy per the QCC Multi-Tenancy
standard. When generating or modifying code here (or in a service created from it), follow the QCP
standards bundled in [`docs/standards/`](docs/standards/). They are the source of truth; this file
is the summary.

## Identity (locked QCP pattern)

- `groupId`: `in.qualtechedge.qcp` — never changes
- `artifactId`: the deployable service name (this template: `java-springboot-multitenant-template`)
- Base package: `in.qualtechedge.qcp.<sub-group>` (this template: `...qcp.templates`)
- Version: `0.0.1-SNAPSHOT` during development; drop `-SNAPSHOT` only for release builds
- Stack: **Spring Boot 4.x · Java 25 · Maven · PostgreSQL 17.6 · Flyway · Hibernate multi-tenancy**

## Hard rules (base template — unchanged)

1. **Configuration**: YAML only (never `.properties`). Common values → `application.yaml`; environment values → `application-<profile>.yaml`. Profiles: `local` (real values), `dev`/`uat`/`prod` (`${UPPERCASE_ENV_VAR}` placeholders, kept aligned across all three).
2. **Package structure** (layer-first, only create packages actually used): `config`, `constant`, `controller`, `dto/request`, `dto/response`, `entity`, `enums`, `exception`, `health`, `mapper`, `multitenancy`, `openapi`, `properties`, `repository`, `scheduler`, `security`, `service` (+ `service/impl`), `utils`.
3. **Imports**: always fully qualified; never wildcard `*` imports.
4. **DTOs**: Java `record`s in `dto/request` / `dto/response`. Request records carry `jakarta.validation` annotations.
5. **Controllers**: thin, `@RestController` + class-level `@RequestMapping`, `@RequiredArgsConstructor`, `@Slf4j`; return `ResponseEntity<APIResponse<T>>` — the `APIResponse` envelope is a **locked QCP contract** (`docs/standards/api-standards.md` §3). URLs: all lowercase, kebab-case, plural nouns.
6. **Services**: interface in `service/`, impl in `service/impl/` (`@Service @RequiredArgsConstructor @Slf4j`). Business logic lives here.
7. **OpenAPI**: Swagger annotations on a documentation interface in `openapi/`; the controller implements it.
8. **Mappers**: dedicated classes in `mapper/`; mapping only.
9. **Database**: schema owned by Flyway, named `V{major}_{minor}_{patch}__{description}.sql` (`V1_0_x` DDL, `V1_1_x` seed, `V1_2_x` patches), one table per file, forward-only, indexed WHERE/JOIN/ORDER BY columns.
10. **Logging**: `@Slf4j`, `{}` placeholders. Controllers log exactly two `INFO` lines per endpoint; failures logged once in `GlobalExceptionHandler`; repositories never log (`docs/standards/logging-standards.md` §5).
11. **Docker**: multi-stage `Dockerfile`, non-root `app` user, QCP ports 9941/9942, `SERVER_PORT` env binding.
12. **POM**: grouped dependencies with one-line comments; `<finalName>${project.artifactId}-${project.version}</finalName>`.

## Multitenancy hard rules (this template — see `docs/standards/multi-tenancy.md`)

13. **Database-per-tenant is the platform standard.** No shared schema, no `tenant_id` column filtering, no schema-per-tenant. Each tenant has its own PostgreSQL database, owned by its own DB role, reached through its own HikariCP pool.
14. **Tenant identity comes only from trusted, server-verified sources**: the `Host` subdomain (`{tenant}-{product}-{env}.domain`) validated against the registry — never from a request body, query param or client-supplied header. Resolution lives in `TenantResolutionFilter` (a servlet filter, NOT a `@ControllerAdvice`): reject before dispatch, clear `HostContext` in `finally`.
15. **Deny by default**: unknown subdomain → `403 Invalid tenant`; no resolvable tenant on a tenant-scoped endpoint → `403`. Excluded prefixes (`qcp.multitenancy.excluded-paths`) run in system scope.
16. **No cross-tenant code path — ever.** `TenantConnectionProvider` throws on an unknown tenant identifier; never add a fallback datasource. `REVOKE CONNECT ... FROM PUBLIC` keeps one tenant's role from even connecting to another tenant's DB. Even superadmin/admin endpoints operate only on the system DB.
17. **System DB** holds the tenant registry (`tenant.tenants`: `short_code`, `db_url`, `status`) and platform data only — never tenant business data. The registry stores `db_url` only; **credentials come from `TenantCredentialProvider`** (config-based in v1, Vault AppRole later) and are never persisted in the registry or entity.
18. **Flyway is split**: `db/migration` (system DB, run by Spring Boot at startup) vs `db/tenant` (run once per tenant database by `TenantProvisioningService`, as the tenant's own role, with the `${tenant_code}` placeholder available). Tenant migrations must work on every tenant DB: single `public` schema, `gen_random_uuid()` (no extensions).
19. **`ddl-auto` stays `none`**: one EntityManagerFactory maps both system entities (registry) and tenant entities, so Hibernate schema validation cannot run against a single DB. Flyway owns both schemas.
20. **New entities go to one side or the other**: tenant-scoped business entities → plain `@Table(name = "...")` (tenant DBs, `public` schema, migration in `db/tenant`); platform entities → `@Table(..., schema = "tenant")` or another system-DB schema (migration in `db/migration`).
21. **Tenant context propagation**: `HostContext` is request-scoped via ThreadLocal; copy it explicitly to worker threads for `@Async`/executors/messaging and clear it after. MDC keys `tenant`/`host` drive the log pattern and the per-tenant SiftingAppender — don't remove them.
22. **Provisioning is runtime, idempotent, restart-free**: registry row + credentials → `TenantProvisioningService.onboard()` (role → `CREATE DATABASE ... OWNER role` → revoke PUBLIC connect → per-tenant Flyway → pool). Run the same PostgreSQL major as the servers (17.6) — 16 changed what `CREATEROLE` grants, so an older local server hides a real failure. System datasource user needs `CREATEDB` + `CREATEROLE`.
23. **Spring Boot 4 gotcha**: the auto-configured ObjectMapper is **Jackson 3** (`tools.jackson.databind.ObjectMapper`) — do not inject `com.fasterxml.jackson.databind.ObjectMapper` (it compiles via transitive deps but has no bean).

26. **The sidebar comes from the database, not the frontend.** `GET /api/v1/menus` answers from
    whichever database the request belongs to — `auth.sidebar_menus` for an administrator,
    `sidebar_menus` in the tenant's own DB for a tenant. Two tables and two entities on purpose:
    the schema is part of the mapping and a tenant DB has no `auth` schema, and a tenant reads its
    own database and nothing else. `SidebarMenuView` keeps that from meaning two of everything
    above it. Adding a screen means adding a row, not editing a list in the frontend. `menu_code`
    is the stable key the frontend matches on; `title` is display text and may be renamed.

## Identity, and what it does to tenant codes

24. **Two identity models, one property.** Blank `spring.keycloak.platform-tenant` means a Keycloak
    of this service's own, a realm per tenant, tenants separated by issuer. Set it and
    identity-portal-service issues the tokens: every application tenant of that platform tenant
    shares **one realm**, the issuer stops separating them, and the **audience** — derived per host
    as `oauth-client-<apptenant>-<product>-<platform-tenant>`, never configured — becomes the only
    thing that does. Never add a fallback that accepts a token whose audience does not match the
    host, and never resolve the realm from the token's own `iss`: that lets the credential choose
    the authority that checks it.
25. **Under the portal, tenant short codes are not yours to invent.** They are the portal's
    application tenant codes, spelled its way, because the audience is derived from them. Seed
    every environment's codes in `V1_1_20`, not just the first one stood up.

Full walk, including the portal-side SQL and the four-request verification:
[`RUN-WITH-IDENTITY-PORTAL.md`](RUN-WITH-IDENTITY-PORTAL.md).

## The example feature

`ExampleEntity` / `ExampleController` / etc. demonstrate the conventions **on the tenant side** (their
data lives in each tenant DB). The `TenantAdmin*` classes demonstrate a **system-scope** feature. When
building a real service from this template, replace the example feature (and its `db/tenant` migrations)
with real features following the same shape; keep the `multitenancy/` package and the tenant admin API.

## Full standards (read when more detail is needed)

- `docs/standards/multi-tenancy.md` — the QCC Multi-Tenancy standard this template implements
- `docs/standards/spring-boot-project-setup.md` — naming, Initializr, POM rules, Actuator
- `docs/standards/spring-boot-project-standards.md` — profiles, structure, DTO/controller/service/OpenAPI rules
- `docs/standards/database-and-flyway-setup.md` — datasource, Flyway versioning, HikariCP
- `docs/standards/docker-and-container-setup.md` — Dockerfile, ports, compose, tagging
- `docs/standards/api-standards.md` — URL naming rules, the locked APIResponse envelope, versioning, pagination
- `docs/standards/logging-standards.md` — Logback pattern, layer logging rules, MDC/correlation
