-- V1_1_0__seed_users.sql
-- Purpose: Seed the console users of the system (superadmin) database, so the seeds that follow
-- have a created_by to point at — auth.roles, sidebar menus and access controls all require one.
-- QCP versioning: V1_1_x = DML seed data
--
-- password is deliberately NULL, and should stay NULL. Identity lives in Keycloak: the realm
-- issues the credential and forces a change on first sign-in. A hash here would be a second
-- credential that nothing rotates and that would still work after the realm account was
-- disabled. Earlier versions of this file shipped BCrypt hashes of 'admin123' and 'user123',
-- which every project cloned from the template inherited — and because they worked, nothing
-- ever prompted anyone to change them.
--
-- Ids are explicit and stable because created_by/updated_by point at users by foreign key;
-- letting each database invent its own is what turns a restore into thousands of orphaned rows.
--
-- RENAME: the email addresses below are placeholders on example.com and must be changed. They
-- are also UNIQUE columns, so leaving them is not merely untidy — two services sharing a
-- database would collide on them.

INSERT INTO auth.users (
    id, username, first_name, last_name, email_id, mobile_number,
    password, password_creation_date, password_expiry_days, password_invalid_attempts,
    status, created_at, send_activation_email, send_activation_sms
) VALUES (
    '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a',
    'admin', 'System', 'Administrator',
    'admin@example.com', '9999999999',
    NULL, NULL, 90, 0, 1, CURRENT_TIMESTAMP, 0, 0
);

-- A second, non-administrator account. Kept because the role and access-control seeds that
-- follow are only meaningful with two users to tell apart; delete it, and V1_1_5's grant for it,
-- if the product has no such distinction.
INSERT INTO auth.users (
    id, username, first_name, last_name, email_id, mobile_number,
    password, password_creation_date, password_expiry_days, password_invalid_attempts,
    status, created_by, created_at, send_activation_email, send_activation_sms
) VALUES (
    '2cf9ea69-045a-4fc6-82d3-a77f7c0de70b',
    'user', 'Regular', 'User',
    'user@example.com', '9999999990',
    NULL, NULL, 90, 0, 1,
    '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a',
    CURRENT_TIMESTAMP, 0, 0
);
