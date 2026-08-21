-- V1_0_47__create_upload_role_processes.sql
-- Purpose: Which processes an upload role grants access to (admin-api-contract.md §3.3 processAccess).

CREATE TABLE upload_role_processes (
  role_id     TEXT NOT NULL REFERENCES upload_roles (role_id) ON DELETE CASCADE,
  process_id  TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,

  PRIMARY KEY (role_id, process_id)
);

CREATE INDEX upload_role_processes_process_idx ON upload_role_processes (process_id);
