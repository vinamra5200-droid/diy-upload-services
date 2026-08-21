-- V1_1_2__seed_api_clients.sql
-- Purpose: Seed the default admin-scope API client.
-- client_secret is bcrypt hash of 'client123' (replace in production).

INSERT INTO auth.api_clients (
    id, client_id, client_secret, name, email_id, description, status, created_by, created_at
) VALUES (
    '2b1148eb-5048-43fd-9638-66d385db4835',
    'admin-client',
    '$2a$10$MmnIXOJrjFiBs.bOgBMKoOeSFAHfqE2I6dkDL8cCLTw2IWC/UODiu', -- client123
    'Default Admin API Client',
    'api-client@qualtechedge.com',
    'Default API client for system/admin operations',
    1,
    '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a',
    CURRENT_TIMESTAMP
);
