-- V1_1_3__seed_api_client_user_roles.sql
-- Purpose: Assign roles to seeded users and API client.

-- admin user → ROLE_ADMIN
INSERT INTO auth.api_client_user_roles (id, user_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a',
        '4e4d9812-11e3-4fae-9d90-4bf06d8cda3a',
        1, '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP);

-- regular user → ROLE_USER
INSERT INTO auth.api_client_user_roles (id, user_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        '2cf9ea69-045a-4fc6-82d3-a77f7c0de70b',
        '6353d61c-62b7-4d0a-bd2d-999e1a4b4162',
        1, '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP);

-- admin-client → ROLE_API_CLIENT
INSERT INTO auth.api_client_user_roles (id, api_client_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        '2b1148eb-5048-43fd-9638-66d385db4835',
        '6311d61c-62b7-4d0a-bd2d-999e1a4b4162',
        1, '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP);
