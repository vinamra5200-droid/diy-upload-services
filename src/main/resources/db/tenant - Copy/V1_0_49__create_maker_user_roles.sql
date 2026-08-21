-- V1_0_49__create_maker_user_roles.sql
-- Purpose: Which upload roles a maker user holds (admin-api-contract.md §4.2 roleIds).

CREATE TABLE maker_user_roles (
  user_id   TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE CASCADE,
  role_id   TEXT NOT NULL REFERENCES upload_roles (role_id) ON DELETE RESTRICT,

  PRIMARY KEY (user_id, role_id)
);

CREATE INDEX maker_user_roles_role_idx ON maker_user_roles (role_id);
