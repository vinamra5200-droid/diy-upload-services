-- V1_1_2__seed_keycloak_batch_upload_users.sql
-- Purpose: same reasoning as V1_1_1, for the case that filter is meant to replace.
--
-- Now that DevBypassAuthenticationFilter is off (application-local.yaml —
-- "Off now that Keycloak is wired up and verified end-to-end"), every /api/v1/upload/*
-- request carries a real Keycloak actorId (the token's `sub`/`actorId` claim) instead of
-- the dev-bypass filter's X-Dev-Actor-Id. listPermittedProcesses/getActiveTemplate/
-- downloadBlankTemplate still resolve a maker_users row for that actorId, so without a row
-- keyed by the real Keycloak id every call 404s with "Maker user not found" — same failure
-- mode V1_1_1 fixed for the dev-bypass actorIds, now for the qc realm's seeded Keycloak users
-- (script/keycloak/import/qc-realm.json: qc.maker-batch-upload, qc.checker-batch-upload).
--
-- Reuses V1_1_1's role-seed-dev-bypass role rather than creating a second one: it already
-- grants exactly the access these users need (the V1_1_0 demo process), and one role covering
-- every seeded local-dev actor is easier to keep in sync than two.
-- QCP versioning: V1_1_x = insert/seed data (DML), patch 2.

INSERT INTO maker_users
  (user_id, username, full_name, is_active, status, created_by)
VALUES
  ('131d70a9-62aa-4b0c-9d5b-de9e5859a738', 'qc.maker-batch-upload', 'qc MakerBatchUpload (seed)', TRUE, 'active', 'seed_script'),
  ('c72f4a2e-a538-4571-8db3-ee32604b93d0', 'qc.checker-batch-upload', 'qc CheckerBatchUpload (seed)', TRUE, 'active', 'seed_script')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO maker_user_roles (user_id, role_id)
VALUES
  ('131d70a9-62aa-4b0c-9d5b-de9e5859a738', 'role-seed-dev-bypass'),
  ('c72f4a2e-a538-4571-8db3-ee32604b93d0', 'role-seed-dev-bypass')
ON CONFLICT (user_id, role_id) DO NOTHING;
