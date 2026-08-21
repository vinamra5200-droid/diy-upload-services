-- V1_0_42__create_template_checker_roles.sql
-- Purpose: Upload-level maker-checker role refs (admin-api-contract.md §2.4 makerChecker.checkerRoles).

CREATE TABLE template_checker_roles (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  role_ref      TEXT NOT NULL,

  PRIMARY KEY (template_id, role_ref),

  CONSTRAINT template_checker_roles_ref_not_blank CHECK (btrim(role_ref) <> '')
);
