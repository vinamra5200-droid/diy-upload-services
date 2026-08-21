-- V1_1_1__seed_roles.sql
-- Purpose: Seed initial roles in every tenant database.
-- role_type: 1 = User role, 2 = API Client role

INSERT INTO roles (id, role_type, name, description, status, created_by, created_at) VALUES
    ('6bcd12b5-5a40-4786-95b3-49c18c7d3956', 1, 'ROLE_TENANT_ADMIN',
     'Administrator role for tenant with all privileges', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP),
    ('c8e22c9f-25c8-4939-b107-6a77dec9c354', 1, 'ROLE_TENANT_USER',
     'Standard user role for tenant with limited privileges', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP),
    ('c8e22c9f-18c8-1239-b107-6a77dec9c354', 2, 'ROLE_TENANT_API_CLIENT',
     'API Client role for tenant with service-to-service access', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- Assign ROLE_TENANT_ADMIN to tenant_admin
INSERT INTO api_client_user_roles (id, user_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
        '6bcd12b5-5a40-4786-95b3-49c18c7d3956',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- Assign ROLE_TENANT_USER to tenant_user
INSERT INTO api_client_user_roles (id, user_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        '2d9ef613-8e52-4ef1-9079-5d4c3871e189',
        'c8e22c9f-25c8-4939-b107-6a77dec9c354',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);
