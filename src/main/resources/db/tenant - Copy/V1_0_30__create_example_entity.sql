-- V1_0_30__create_example_entity.sql
-- Purpose: Create the example_entity table in EVERY tenant database
--
-- db/tenant migrations run once per tenant database (TenantProvisioningService) as the
-- tenant's own DB role — every tenant DB gets an identical, versioned schema. Single
-- 'public' schema per tenant database (QCP standard); gen_random_uuid() needs no extension.

CREATE TABLE example_entity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0  -- optimistic locking counter managed by JPA @Version
);

-- Index for name lookups (used in WHERE/ORDER BY)
CREATE INDEX idx_example_entity_name ON example_entity(name);
-- Index for time-based listing queries
CREATE INDEX idx_example_entity_created_at ON example_entity(created_at);

-- Audit trigger function: keeps updated_at current on UPDATE
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Audit trigger
CREATE TRIGGER update_example_entity_updated_at
    BEFORE UPDATE ON example_entity
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
