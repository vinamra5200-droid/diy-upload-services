-- V1_0_9__create_role_access_controls.sql
-- Purpose: JSON-based permission sets assigned per role (RBAC).

-- =============================================
-- Role access controls table for role-based permissions
-- =============================================

-- Stores JSON-based access permissions for each role
CREATE TABLE IF NOT EXISTS auth.role_access_controls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    access_json TEXT NOT NULL,     -- JSON structure defining permissions/access rights
    status INTEGER NOT NULL,       -- 1=active, 0=inactive
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_rac_role_id FOREIGN KEY (role_id)
        REFERENCES auth.roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rac_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_rac_updated_by FOREIGN KEY (updated_by)
        REFERENCES auth.users(id) ON DELETE SET NULL
);

-- Speed up lookups by role
CREATE INDEX IF NOT EXISTS idx_role_access_controls_role_id ON auth.role_access_controls (role_id);

-- Speed up filtering by status
CREATE INDEX IF NOT EXISTS idx_role_access_controls_status ON auth.role_access_controls (status);
