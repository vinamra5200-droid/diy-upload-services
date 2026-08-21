-- V1_0_7__create_api_client_user_tokens.sql
-- Purpose: JWT access/refresh token lifecycle management for admin users and API clients.

-- =============================================
-- auth schema tables (continued)
-- =============================================

-- Token management for JWT access and refresh tokens
CREATE TABLE IF NOT EXISTS auth.api_client_user_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_type INTEGER NOT NULL,           -- 1=User, 2=API Client
    user_id UUID,                              -- User ID (FK added below)
    api_client_id UUID,                        -- API Client ID (FK added below)
    token_type INTEGER NOT NULL,               -- 1=Access Token, 2=Refresh Token
    token VARCHAR(512) NOT NULL,               -- Encoded token value
    user_agent VARCHAR(512) NOT NULL,          -- Client user-agent string
    ip_address VARCHAR(45),                    -- IP address at token creation
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,  -- Token expiration
    status INTEGER NOT NULL,                   -- Token status (1=active, 0=revoked)
    created_by_type INTEGER NOT NULL,          -- Discriminator for creator entity type
    created_by UUID NOT NULL,                  -- Reference to creating entity
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_type INTEGER,                   -- Discriminator for updater entity type
    updated_by UUID,                           -- Reference to updating entity
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Support filtering by principal and token type
CREATE INDEX IF NOT EXISTS idx_api_client_user_tokens_principal_type ON auth.api_client_user_tokens (principal_type);
CREATE INDEX IF NOT EXISTS idx_api_client_user_tokens_user_id ON auth.api_client_user_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_api_client_user_tokens_api_client_id ON auth.api_client_user_tokens (api_client_id);
CREATE INDEX IF NOT EXISTS idx_api_client_user_tokens_token_type ON auth.api_client_user_tokens (token_type);
CREATE INDEX IF NOT EXISTS idx_api_client_user_tokens_expires_at ON auth.api_client_user_tokens (expires_at);
CREATE INDEX IF NOT EXISTS idx_api_client_user_tokens_status ON auth.api_client_user_tokens (status);

-- Composite index for active token lookups
CREATE INDEX IF NOT EXISTS idx_api_client_user_tokens_active
    ON auth.api_client_user_tokens (user_id, token_type, status, expires_at)
    WHERE status = 1;

-- =============================================
-- Foreign key constraints for auth.api_client_user_tokens
-- =============================================

ALTER TABLE auth.api_client_user_tokens
    ADD CONSTRAINT fk_api_client_user_tokens_user_id FOREIGN KEY (user_id)
        REFERENCES auth.users (id)
        ON DELETE CASCADE;

ALTER TABLE auth.api_client_user_tokens
    ADD CONSTRAINT fk_api_client_user_tokens_api_client_id FOREIGN KEY (api_client_id)
        REFERENCES auth.api_clients (id)
        ON DELETE CASCADE;
