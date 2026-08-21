-- V1_0_2__create_roles.sql
-- Purpose: Role catalog scoped to each tenant database.

-- =============================================
-- Tenant roles table
-- =============================================

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_type INTEGER NOT NULL,
    name VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_roles_role_type ON roles(role_type);
CREATE INDEX IF NOT EXISTS idx_roles_status ON roles(status);
