# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial QCP **multitenant** Spring Boot 4 service template (Java 25, Maven), built on
  `java-springboot-template` and implementing the QCC Multi-Tenancy standard
  (database-per-tenant; reference implementation: kyc-service).
- **Tenant resolution**: `TenantResolutionFilter` resolves the tenant from the `Host` subdomain
  (`{tenant}-{product}-{env}.domain`), validates it against the registry and rejects unknown or
  unresolvable tenants with the locked `APIResponse` envelope (`QT-TEN-403`, deny by default).
- **Tenant context**: `HostContext` ThreadLocal + MDC (`tenant`, `host`), cleared in `finally`.
- **Database-per-tenant isolation**: Hibernate `TenantIdentifierResolver` +
  `TenantConnectionProvider` route to per-tenant HikariCP pools (`TenantDataSourceManager`);
  unknown tenant identifiers fail hard — no fallback datasource; tenant DB roles own their
  databases and `PUBLIC` connect is revoked (cross-tenant connections impossible).
- **System (superadmin) database** with the tenant registry (`tenant.tenants`) via `db/migration`
  Flyway migrations; seeds demo tenants `qc`, `client1`, `client2`.
- **Runtime provisioning** (`TenantProvisioningService` + `TenantStartupInitializer`): role +
  `CREATE DATABASE ... OWNER` + per-tenant `db/tenant` Flyway (with `${tenant_code}` placeholder)
  + pool registration — new tenants need no restart.
- **Tenant admin API** (`/api/v1/admin/tenants`, system scope): list registry + onboard a tenant
  live end to end.
- **Credential seam**: `TenantCredentialProvider` interface with config-based v1 implementation
  (`qcp.multitenancy.tenants.*`); Vault (AppRole) implementation planned to replace it on server
  environments.
- **Per-tenant log files**: Logback SiftingAppender keyed on MDC `tenant`
  (`logs/mt-template-<tenant>.log`), local pattern + Logstash JSON variants.
- `ConflictException` (409 `QT-RES-409`) and `TenantProvisioningException` (500 `QT-TEN-500`)
  handling in the global exception handler.
- Example CRUD feature moved to the tenant side: its table/seeds live in every tenant database,
  making isolation visible (`Sample Example A (tenant client1)` vs `... (tenant client2)`).

### Notes

- Requires PostgreSQL 15+; the system datasource user needs `CREATEDB` + `CREATEROLE`.
- `spring.jpa.hibernate.ddl-auto` is `none` (one EMF maps system + tenant entities; Flyway owns both schemas).
- Spring Boot 4 auto-configures Jackson 3 (`tools.jackson`) — injected in the resolution filter.
