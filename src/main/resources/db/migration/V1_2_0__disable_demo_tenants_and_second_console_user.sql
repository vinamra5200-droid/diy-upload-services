-- V1_2_0__disable_demo_tenants_and_second_console_user.sql
-- Purpose: Leave only 'qc' active in the tenant registry and only 'admin' active in the console
-- user seed. QCP versioning: V1_2_x = DML patches — V1_1_0/V1_1_20 already ran, so this updates
-- rather than edits those seeds (editing an applied migration breaks Flyway's checksum).
--
-- client1/client2 stay in tenant.tenants for reference but status = 0 makes them non-routable:
-- TenantResolutionFilter 403s any request against their hosts, and TenantStartupInitializer
-- (WHERE status = 1) excludes them from pool provisioning at startup.
UPDATE tenant.tenants SET status = 0 WHERE short_code IN ('client1', 'client2');

-- 'user' (the non-administrator demo account from V1_1_0) is disabled so 'admin' is the only
-- active console user in the system database.
UPDATE auth.users SET status = 0 WHERE username = 'user';
