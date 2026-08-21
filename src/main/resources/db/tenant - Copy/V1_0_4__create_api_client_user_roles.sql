-- V1_0_4__create_api_client_user_roles.sql
-- Purpose: Role assignments for tenant users and API clients.

-- =============================================
-- Tenant API client user roles table
-- =============================================

CREATE TABLE IF NOT EXISTS api_client_user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    api_client_id UUID,
    role_id UUID NOT NULL,
    status INTEGER NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_acur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_api_client FOREIGN KEY (api_client_id) REFERENCES api_clients(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_acur_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_acur_user_role ON api_client_user_roles(user_id, api_client_id, role_id);
