-- V1_0_2__create_roles.sql
-- Purpose: Role catalog for the auth domain (admin users and API clients).

-- =============================================
-- auth schema tables
-- =============================================

-- Role catalog for the authentication domain
CREATE TABLE IF NOT EXISTS auth.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_type INTEGER NOT NULL,               -- 1 = User (auth.users), 2 = API Client (auth.api_clients)
    name VARCHAR(50) NOT NULL,                -- Human-readable role label displayed in UIs
    description TEXT NOT NULL,                -- Extended description for auditors/operators
    status INTEGER NOT NULL,                  -- Tracks lifecycle (draft/active/inactive)
    created_by UUID NOT NULL,                 -- Actor who created the role definition (FK added later)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,                          -- Actor who last modified the role (FK added later)
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Accelerate queries that filter roles by type or status
CREATE INDEX IF NOT EXISTS idx_roles_role_type ON auth.roles (role_type);
CREATE INDEX IF NOT EXISTS idx_roles_status ON auth.roles (status);

-- Ensure fast lookups by role name (even if not unique at DB level)
CREATE INDEX IF NOT EXISTS idx_roles_name ON auth.roles (LOWER(name));
