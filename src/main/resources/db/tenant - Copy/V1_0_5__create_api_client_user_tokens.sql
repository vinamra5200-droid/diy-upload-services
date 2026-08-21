-- V1_0_5__create_api_client_user_tokens.sql
-- Purpose: JWT access/refresh token lifecycle management for tenant principals.

-- =============================================
-- Tenant API client user tokens table
-- =============================================

CREATE TABLE IF NOT EXISTS api_client_user_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_type INTEGER NOT NULL,
    user_id UUID,
    api_client_id UUID,
    token_type INTEGER NOT NULL,
    token VARCHAR(1024) NOT NULL,
    user_agent VARCHAR(512),
    ip_address VARCHAR(45),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status INTEGER NOT NULL,
    created_by_type INTEGER NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_type INTEGER,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_acut_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_acut_client_id FOREIGN KEY (api_client_id) REFERENCES api_clients(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_acut_user_token ON api_client_user_tokens(user_id, token);
CREATE INDEX IF NOT EXISTS idx_acut_api_client_id ON api_client_user_tokens(api_client_id);
CREATE INDEX IF NOT EXISTS idx_acut_status ON api_client_user_tokens(status);
