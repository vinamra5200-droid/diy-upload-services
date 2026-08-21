# Multi-Tenancy (QCC)

**Phase 1 · Status: ✅ Active**

**Qualtech Core Components (QCC)** are the shared, reusable building blocks every QCP service adopts so cross-cutting capabilities behave identically across the platform. This page describes the **Multi-Tenancy** core component — the model below is the **final, implemented design** (reference implementation: kyc-service).

Qualtech builds BFSI products that serve **many tenants** (banks, NBFCs, insurers and their branches/business units) from the same deployment. The multi-tenancy component makes tenant **isolation, context and configuration** a built-in default rather than something each service re-invents.

---

## 1. What it provides

| Capability | Description |
|------------|-------------|
| **Tenant resolution** | Subdomain for applications; API client identity (from the token) for internal service APIs. |
| **Tenant context** | Propagates the resolved tenant through the request — service calls, async tasks, scheduled jobs and logs. |
| **Data isolation** | **Database-per-tenant** — a tenant can physically only reach its own data. |
| **Per-tenant configuration** | Tenant-scoped settings and secrets (DB credentials, keys) served from Vault. |
| **Observability** | `tenant` in MDC on every log line, plus a **separate log file per tenant**. |

---

## 2. Tenant resolution

The tenant is **never** taken from user-supplied request bodies or query params. Resolution depends on the caller type:

### 2.1 Applications (browser traffic) — by subdomain

Each tenant gets its own subdomain on the product domain: **`{tenant}-{product}-{env}.qualtechedge.in`**

```text
https://qc-custconnect-dev.qualtechedge.in/        → tenant: qc (Qualtech)
https://admin-custconnect-dev.qualtechedge.in/     → superadmin console
https://client1-custconnect-dev.qualtechedge.in/   → tenant: client1
https://client2-custconnect-dev.qualtechedge.in/   → tenant: client2
…
```

- A request interceptor reads the **`Host` header**, extracts the subdomain and looks it up in the **tenant registry** (`tenant.tenants.short_code` in the system database).
- Unknown subdomain → request **rejected** (`Invalid Tenant`, deny by default).
- Specific non-tenant endpoints (e.g. provider webhooks) are explicitly excluded from resolution.

### 2.2 Internal service APIs — by API client identity

Service-to-service and partner calls do not carry a subdomain; the tenant comes from the **authenticated API client**:

- The caller authenticates with its **`api-client-id`** (API key + secret).
- The issued **token identifies the client**, and the client record is bound to its tenant — a `TenantApiClient` resolves to exactly one tenant; platform-level `ApiClient`s belong to the superadmin scope.
- The auth filter resolves the tenant from the validated token **before** any business code runs.

If no tenant can be resolved on a tenant-scoped endpoint, the request is **rejected**.

---

## 3. Tenant context propagation

- A **`HostContext`** (`ThreadLocal`) holds the current tenant (and host); the interceptor/auth filter populates it early and **clears it** at the end of the request — no leakage across pooled threads.
- The tenant is added to the logging **MDC** (`tenant=%X{tenant:-system}` — see [`logging-standards.md`](../logging-standards.md)) so every log line is attributable.
- For async (`@Async`, executors) and messaging, the context is **explicitly copied** to the worker thread / message headers.

---

## 4. Database isolation — database-per-tenant (locked)

QCP uses the **strongest isolation model**: every tenant has its **own database with its own connection string** — there is no shared schema and no `tenant_id` column filtering.

```text
                       ┌──────────────────────────────┐
 request (tenant=X) ──▶│ Hibernate                    │
                       │  CurrentTenantIdentifier     │──▶ routes to ──┐
                       │  MultiTenantConnectionProvider│               │
                       └──────────────────────────────┘               ▼
 ┌────────────────┐   ┌────────────────┐   ┌────────────────┐   ┌────────────────┐
 │  system DB     │   │  client1 DB    │   │  client2 DB    │   │  clientN DB    │
 │  (superadmin)  │   │  (pool 1)      │   │  (pool 2)      │   │  (pool N)      │
 │  tenant registry│  └────────────────┘   └────────────────┘   └────────────────┘
 └────────────────┘
```

| Rule | Detail |
|---|---|
| **One database per tenant** | Separate PostgreSQL database, separate connection string, separate HikariCP pool per tenant. |
| **One system (superadmin) database** | Holds the **tenant registry** (`tenant.tenants`: `short_code`, `db_url`, `status`) and platform/admin data — **no tenant business data**. The only database that exists up front. |
| **Tenant databases are created at runtime** | There is no fixed set of tenant DBs: when a tenant is onboarded, its database is **provisioned dynamically** (tenant setup service), registered in the registry, migrated by Flyway and pooled — the number of tenant DBs simply grows with the number of tenants, **no deployment or restart**. |
| **No cross-tenant access — ever** | A tenant's pool can only reach that tenant's DB. **Even superadmin cannot query tenant databases**; admin operations work exclusively on the system DB. There is no code path for a cross-tenant query. |
| **Credentials from Vault** | The registry stores only the `db_url`; per-tenant DB **username/password are fetched from Vault** (AppRole auth) at pool creation — never stored in the registry or config files. |
| **Pools at startup + lazy registration** | On `ApplicationReady` the service builds a pool per ACTIVE registry tenant; a **new tenant is picked up lazily without an application restart**. |
| **Routing** | Hibernate `CurrentTenantIdentifierResolver` reads the tenant from `HostContext`; `MultiTenantConnectionProvider` hands out a connection from that tenant's pool. |
| **Per-tenant Flyway** | Migrations run **per tenant database** via a migration orchestrator at startup (and on tenant provisioning) — every tenant DB has an identical, versioned schema. |

---

## 5. Per-tenant logging

- Every log line carries `tenant=` from MDC (local pattern) / a `tenant` JSON field (server profiles) — `logging-standards.md`.
- In addition, each tenant writes to a **separate log file**, keyed by the tenant in MDC — per-tenant diagnostics and support bundles never mix tenants' traffic.

---

## 6. Per-tenant cloud isolation

Isolation extends beyond the database to cloud resources:

| Cloud | Isolation unit |
|---|---|
| **AWS** | A **separate S3 bucket per tenant** (documents, evidence, recordings). |
| **Azure** | A **separate resource group per tenant**. |

No shared buckets/containers with tenant-prefixed paths — the **resource itself** is the isolation boundary, so IAM policies and lifecycle rules are tenant-scoped too.

---

## 7. Standards & rules

- Tenant identity comes only from **trusted, server-verified sources** — subdomain validated against the registry, or the authenticated API client's token. Never from a request body or query param.
- **Database-per-tenant is the platform standard** — shared-schema (`tenant_id` column) and schema-per-tenant designs are not used in QCP.
- Cross-tenant queries are impossible by construction and **must stay that way** — never introduce a datasource that can reach more than one tenant DB.
- Tenant DB credentials live in **Vault only**.
- Tenant context must be **cleared** at request end and **propagated** across async/messaging boundaries.
- `tenant` is included in logs (MDC + per-tenant file), metrics and traces.
- New-tenant provisioning = registry row + Vault credentials + DB + Flyway + bucket/resource group — **no service restart**.

---

*Part of the Qualtech Engineering Framework — Phase 1, Qualtech Core Components.*
