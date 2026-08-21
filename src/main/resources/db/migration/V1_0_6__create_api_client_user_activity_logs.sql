-- V1_0_6__create_api_client_user_activity_logs.sql
-- Purpose: Audit trail for user and API-client events (login, logout, password change, etc.).

-- =============================================
-- auth schema tables (continued)
-- =============================================

-- Activity log for tracking user and API client events (login, logout, etc.)
CREATE TABLE IF NOT EXISTS auth.api_client_user_activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,                              -- Reference to auth.users if actor is a user
    api_client_id UUID,                        -- Reference to auth.api_clients if actor is a client
    event_type INTEGER NOT NULL,               -- Event discriminator (login, logout, password change, etc.)
    ip_address VARCHAR(45),                    -- IPv4 or IPv6 address
    user_agent VARCHAR(512),                   -- Client user-agent string
    latitude DECIMAL(9,6),                     -- Geo-location latitude
    longitude DECIMAL(9,6),                    -- Geo-location longitude
    app_version VARCHAR(50),                   -- Mobile/web app version
    device_id VARCHAR(100),                    -- Unique device identifier
    created_by_type INTEGER NOT NULL,          -- Discriminator for creator entity type
    created_by UUID NOT NULL,                  -- Reference to creating entity
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Speed up lookups by user
CREATE INDEX IF NOT EXISTS idx_acual_user_id ON auth.api_client_user_activity_logs (user_id);

-- Speed up lookups by API client
CREATE INDEX IF NOT EXISTS idx_acual_api_client_id ON auth.api_client_user_activity_logs (api_client_id);

-- Speed up lookups by event type
CREATE INDEX IF NOT EXISTS idx_acual_event_type ON auth.api_client_user_activity_logs (event_type);

-- Speed up lookups by creation time
CREATE INDEX IF NOT EXISTS idx_acual_created_at ON auth.api_client_user_activity_logs (created_at);

-- Composite index for common queries
CREATE INDEX IF NOT EXISTS idx_acual_user_created_at
    ON auth.api_client_user_activity_logs (user_id, created_at DESC);

-- =============================================
-- Foreign key constraints for auth.api_client_user_activity_logs
-- =============================================

ALTER TABLE auth.api_client_user_activity_logs
    ADD CONSTRAINT fk_acual_user_id FOREIGN KEY (user_id)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;

ALTER TABLE auth.api_client_user_activity_logs
    ADD CONSTRAINT fk_acual_api_client_id FOREIGN KEY (api_client_id)
        REFERENCES auth.api_clients (id)
        ON DELETE SET NULL;
