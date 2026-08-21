-- V1_1_2__seed_api_clients.sql
-- Purpose: Seed the default API client(s) in every tenant database.
-- Uses ${tenant_code} placeholder (resolved per-tenant by TenantProvisioningService).
-- client_secret BCrypt hashes use 'client123' — replace in production.

-- Internal service-to-service API client (client_credentials grant via Keycloak)
INSERT INTO api_clients (
    id, client_id, client_secret, name, email_id, description, status, created_by, created_at
) VALUES (
    'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b6',
    'tenant_default_${tenant_code}_client',
    '$2a$10$MmnIXOJrjFiBs.bOgBMKoOeSFAHfqE2I6dkDL8cCLTw2IWC/UODiu', -- client123
    'Default ${tenant_code} API Client',
    'tenant_default_${tenant_code}_client@example.com',
    'Default internal API client for tenant ${tenant_code} service-to-service access',
    1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);

-- Customer-facing public API client
INSERT INTO api_clients (
    id, client_id, client_secret, name, email_id, description, status, created_by, created_at
) VALUES (
    'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b7',
    'tenant_default_${tenant_code}_customer_client',
    '$2a$10$aNeRPicjYJER28B4cymPHebn7LeY1E07hY13Nkgl.4HL21MZSy5c.', -- TenantDef@ultQcpClient123
    'Default ${tenant_code} Customer API Client',
    'tenant_default_${tenant_code}_customer_client@example.com',
    'Default customer-facing API client for tenant ${tenant_code}',
    1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);

-- Assign ROLE_TENANT_API_CLIENT to internal client
INSERT INTO api_client_user_roles (id, api_client_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b6',
        'c8e22c9f-18c8-1239-b107-6a77dec9c354',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- Assign ROLE_TENANT_API_CLIENT to customer client
INSERT INTO api_client_user_roles (id, api_client_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b7',
        'c8e22c9f-18c8-1239-b107-6a77dec9c354',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);
