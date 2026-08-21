-- V1_0_5__create_api_client_user_roles.sql
-- Purpose: Junction table linking both users and API clients to their assigned roles.

-- =============================================
-- auth schema tables (continued)
-- =============================================

-- Junction table linking users/API clients to their assigned roles
-- Supports both user-role and api_client-role relationships
CREATE TABLE IF NOT EXISTS auth.api_client_user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,                              -- User associated with this role assignment (null for API client roles)
    api_client_id UUID,                        -- API client associated with this role assignment (null for user roles)
    role_id UUID NOT NULL,                     -- The role being assigned
    status INTEGER NOT NULL,                   -- Role assignment status (1=active, 0=revoked)
    created_by UUID NOT NULL,                  -- User who created this role assignment
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,                           -- User who last modified this role assignment
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_acur_user FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_api_client FOREIGN KEY (api_client_id) REFERENCES auth.api_clients(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_role FOREIGN KEY (role_id) REFERENCES auth.roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_created_by FOREIGN KEY (created_by) REFERENCES auth.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_acur_updated_by FOREIGN KEY (updated_by) REFERENCES auth.users(id) ON DELETE SET NULL
);

-- Speed up role lookups by user
CREATE INDEX IF NOT EXISTS idx_acur_user_id ON auth.api_client_user_roles (user_id);

-- Speed up role lookups by API client
CREATE INDEX IF NOT EXISTS idx_acur_api_client_id ON auth.api_client_user_roles (api_client_id);

-- Speed up role lookups by role
CREATE INDEX IF NOT EXISTS idx_acur_role_id ON auth.api_client_user_roles (role_id);

-- Speed up filtering by status
CREATE INDEX IF NOT EXISTS idx_acur_status ON auth.api_client_user_roles (status);
