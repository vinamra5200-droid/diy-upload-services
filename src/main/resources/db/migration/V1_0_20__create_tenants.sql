-- V1_0_20__create_tenants.sql
-- Purpose: Create the tenant registry (table #1 — patch number matches creation order)
--
-- The registry stores only the connection string (db_url); per-tenant DB credentials come
-- from the TenantCredentialProvider (config in v1, Vault on server environments) and are
-- NEVER stored here.

CREATE TABLE tenant.tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    short_code VARCHAR(20) NOT NULL UNIQUE,    -- subdomain segment, lowercase
    db_url VARCHAR(500) NOT NULL,              -- JDBC URL of the tenant's isolated database
    status INTEGER NOT NULL DEFAULT 1,         -- 1 = active (routable), 0 = inactive
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for resolution lookups (every request checks short_code + status)
CREATE INDEX idx_tenants_short_code_status ON tenant.tenants(short_code, status);

-- Audit trigger function: keeps updated_at current on UPDATE
CREATE OR REPLACE FUNCTION tenant.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Audit trigger
CREATE TRIGGER update_tenants_updated_at
    BEFORE UPDATE ON tenant.tenants
    FOR EACH ROW
    EXECUTE FUNCTION tenant.update_updated_at_column();
