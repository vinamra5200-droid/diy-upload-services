-- V1_1_30__seed_example_entity.sql
-- Purpose: Seed per-tenant reference data into example_entity
-- QCP versioning: V1_1_x = insert/seed data (DML), patch 1 = example_entity table
--
-- ${tenant_code} is supplied by TenantProvisioningService when migrating each tenant
-- database — the seeded rows name their tenant, making DB-per-tenant isolation visible
-- when the same endpoint is called with different Host headers.

INSERT INTO example_entity (name, description) VALUES
('Sample Example A (tenant ${tenant_code})', 'Seeded row in the isolated ${tenant_code} database'),
('Sample Example B (tenant ${tenant_code})', 'Second seeded row for tenant ${tenant_code}');
