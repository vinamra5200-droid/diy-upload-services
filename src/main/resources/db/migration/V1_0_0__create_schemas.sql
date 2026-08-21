-- V1_0_0__create_schemas.sql
-- Purpose: Create the top-level schemas of the SYSTEM (superadmin) database.
-- QCP versioning: V1_0_x = DDL (schema/table creation), patch 0 = schemas
--
-- The system database is the only database that exists up front; it holds the tenant
-- registry, admin users/roles/clients, and platform config — never tenant business data
-- (QCC Multi-Tenancy §4).
--
-- No CREATE EXTENSION. This used to enable uuid-ossp for uuid_generate_v4(); every table now
-- uses gen_random_uuid(), which has been built into PostgreSQL since 13 and this platform
-- requires 15. Creating an extension is a privilege a migration should not need — and the
-- tenant migrations run as each tenant's own role, which may not have it.

-- Authentication domain: admin users, roles, API clients, tokens
CREATE SCHEMA IF NOT EXISTS auth;

-- Image storage: uploaded binary assets (profile pictures, logos, documents)
CREATE SCHEMA IF NOT EXISTS image;

-- Tenant registry: tenant metadata, languages, provisioning status
CREATE SCHEMA IF NOT EXISTS tenant;

