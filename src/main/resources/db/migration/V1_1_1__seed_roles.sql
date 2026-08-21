-- V1_1_1__seed_roles.sql
-- Purpose: Seed system roles (ROLE_ADMIN, ROLE_USER, ROLE_API_CLIENT) for the admin realm.
-- role_type: 1 = User role, 2 = API Client role

INSERT INTO auth.roles (id, role_type, name, description, status, created_by, created_at) VALUES
    ('4e4d9812-11e3-4fae-9d90-4bf06d8cda3a', 1, 'ROLE_ADMIN',
     'System Administrator role with all privileges', 1,
     '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP),
    ('6353d61c-62b7-4d0a-bd2d-999e1a4b4162', 1, 'ROLE_USER',
     'Standard user with basic privileges', 1,
     '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP),
    ('6311d61c-62b7-4d0a-bd2d-999e1a4b4162', 2, 'ROLE_API_CLIENT',
     'API Client with basic system access privileges', 1,
     '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP);
