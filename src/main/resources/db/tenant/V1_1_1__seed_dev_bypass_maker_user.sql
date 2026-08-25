-- V1_1_1__seed_dev_bypass_maker_user.sql
-- Purpose: give the upload-operator API (upload-api-contract.md §1) something to authorize against
-- locally. DevBypassAuthenticationFilter fabricates authentication (and now every role — see its
-- class doc) for whatever actorId the caller sends (X-Dev-Actor-Id, default "maker_admin_01") when
-- no Authorization header is sent, but §1's listPermittedProcesses/getActiveTemplate/
-- downloadBlankTemplate still do a real data-level check: they resolve a maker_users row for that
-- actorId and look at the upload_roles it holds via maker_user_roles -> upload_role_processes.
-- Without a seeded row here, every /api/v1/upload/* call 404s with "Maker user not found" even
-- though authentication/authorization at the filter level succeeds.
--
-- "maker_users" holds both maker- and checker-side upload operators (V1_0_48's table comment:
-- "not the Maker Admin actor who owns this table's rows") — the dev-bypass filter grants every
-- actorId both makerBatchUpload and checkerBatchUpload, so both a maker and a checker dev actor
-- are seeded here, sharing the one demo role/process grant.
--
-- Same reasoning as V1_1_0's vendor-onboarding seed: reference/demo data every tenant gets, not
-- tenant-specific business data, so the demo process seeded there is immediately exercisable
-- through the upload-operator API too, not just the admin API.
-- QCP versioning: V1_1_x = insert/seed data (DML), patch 1.

INSERT INTO maker_users
  (user_id, username, full_name, is_active, status, created_by)
VALUES
  ('maker_admin_01', 'dev.bypass.maker', 'Dev Bypass Maker Actor (seed)', TRUE, 'active', 'seed_script'),
  ('checker_admin_01', 'dev.bypass.checker', 'Dev Bypass Checker Actor (seed)', TRUE, 'active', 'seed_script');

INSERT INTO upload_roles
  (role_id, role_name, description, is_active, status, created_by)
VALUES
  ('role-seed-dev-bypass', 'Dev Bypass Upload Access (seed)',
   'Grants the seeded dev-bypass maker/checker users access to the V1_1_0 vendor-onboarding demo process.',
   TRUE, 'active', 'seed_script');

INSERT INTO upload_role_processes (role_id, process_id)
VALUES
  ('role-seed-dev-bypass', 'proc-000001');

INSERT INTO maker_user_roles (user_id, role_id)
VALUES
  ('maker_admin_01', 'role-seed-dev-bypass'),
  ('checker_admin_01', 'role-seed-dev-bypass');
