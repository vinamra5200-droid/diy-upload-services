-- V1_1_20__seed_tenants.sql
-- Purpose: Seed the tenant registry with the template's demo tenants
-- QCP versioning: V1_1_x = insert/seed data (DML), patch 1 = tenants table
--
-- ${db_host} / ${db_port} are Flyway placeholders supplied per profile
-- (spring.flyway.placeholders.* in application-<profile>.yaml) so the seeded
-- db_url always points at the environment's database server.
--
-- The superadmin scope is NOT a tenant row — admin-* subdomains and the admin API
-- run against the system database itself.

INSERT INTO tenant.tenants (name, description, short_code, db_url, status) VALUES
('Qualtech',  'Qualtech in-house tenant',          'qc',      'jdbc:postgresql://${db_host}:${db_port}/mt-template-qc-db',      1),
('Client One', 'First demo client tenant',          'client1', 'jdbc:postgresql://${db_host}:${db_port}/mt-template-client1-db', 1),
('Client Two', 'Second demo client tenant',         'client2', 'jdbc:postgresql://${db_host}:${db_port}/mt-template-client2-db', 1);
