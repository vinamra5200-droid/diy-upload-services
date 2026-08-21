-- V1_0_4__create_api_clients.sql
-- Purpose: API client registry for machine-to-machine authentication.

-- =============================================
-- auth schema tables (continued)
-- =============================================

-- API client registry for machine-to-machine authentication
CREATE TABLE IF NOT EXISTS auth.api_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id VARCHAR(50) NOT NULL UNIQUE,     -- Public identifier for OAuth flows
    client_secret VARCHAR(255) NOT NULL,        -- Hashed secret for authentication
    name VARCHAR(50) NOT NULL,                  -- Human-readable client name
    email_id VARCHAR(255) NOT NULL UNIQUE,      -- Contact email for the client owner
    description TEXT NOT NULL,                  -- Purpose or scope of the API client
    status INTEGER NOT NULL,                    -- Lifecycle state (active, revoked, etc.)
    created_by UUID NOT NULL,                   -- User who registered the client (FK added below)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,                            -- User who last modified the client (FK added below)
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Speed up lookups by status
CREATE INDEX IF NOT EXISTS idx_api_clients_status ON auth.api_clients (status);

-- =============================================
-- Foreign key constraints for auth.api_clients
-- =============================================

ALTER TABLE auth.api_clients
    ADD CONSTRAINT fk_api_clients_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;

ALTER TABLE auth.api_clients
    ADD CONSTRAINT fk_api_clients_updated_by FOREIGN KEY (updated_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;
