-- V1_1_0__seed_admin_user.sql
-- Purpose: One administrator per tenant database, so a freshly onboarded tenant has somebody
-- who can sign in. Applied per tenant by TenantProvisioningService (db/tenant location).
--
-- password is NULL, and must stay NULL. The credential belongs to the tenant's Keycloak realm,
-- which issues a temporary one and forces a change on first sign-in. A hash here would be a
-- second credential that nothing rotates and that would still work after the realm account was
-- disabled. This file previously carried BCrypt hashes of 'admin123' and 'user123' — the same
-- two in every tenant of every project cloned from this template.
--
-- Seeding a row here rather than only in Keycloak keeps the SQL as the single place a starting
-- user is declared, with the realm following it, instead of the two being maintained separately.
--
-- Ids are explicit and stable: created_by/updated_by point at users by foreign key, so letting
-- each tenant database invent its own would make the same administrator a different id per
-- tenant, and nothing that compares two tenants would line up. They are also deliberately
-- different from the admin-side ids in db/migration/V1_1_0, so the two are never confused.

-- Tenant administrator. created_by points at itself: it is the first row, and there is nobody
-- else for it to point at.
INSERT INTO users (
    id, username, first_name, last_name, email_id, mobile_number,
    send_activation_email, send_activation_sms,
    password, password_creation_date, password_expiry_days, password_invalid_attempts,
    status, created_by, created_at
) VALUES (
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    'tenant_admin', 'Tenant', 'Admin',
    'tenant_admin@example.com', '9876543210',
    0, 0,
    NULL, NULL, 90, 0, 1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);

-- A second, non-administrator account, so the role and access-control model has two users to
-- tell apart. Delete it if the product has no such distinction.
INSERT INTO users (
    id, username, first_name, last_name, email_id, mobile_number,
    send_activation_email, send_activation_sms,
    password, password_creation_date, password_expiry_days, password_invalid_attempts,
    status, created_by, created_at
) VALUES (
    '2d9ef613-8e52-4ef1-9079-5d4c3871e189',
    'tenant_user', 'Tenant', 'User',
    'tenant_user@example.com', '9876543200',
    0, 0,
    NULL, NULL, 90, 0, 1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);
