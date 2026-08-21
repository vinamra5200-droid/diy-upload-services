-- V1_0_10__create_user_access_controls.sql
-- Purpose: Per-user JSON permission overrides (take precedence over role defaults).

-- =============================================
-- User access controls table for user-specific permissions
-- =============================================

-- Stores JSON-based access permissions for individual users (overrides role permissions)
CREATE TABLE IF NOT EXISTS auth.user_access_controls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    access_json TEXT NOT NULL,     -- JSON structure defining permissions/access rights
    status INTEGER NOT NULL,       -- 1=active, 0=inactive
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_uac_user_id FOREIGN KEY (user_id)
        REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_uac_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_uac_updated_by FOREIGN KEY (updated_by)
        REFERENCES auth.users(id) ON DELETE SET NULL
);

-- Speed up lookups by user
CREATE INDEX IF NOT EXISTS idx_user_access_controls_user_id ON auth.user_access_controls (user_id);

-- Speed up filtering by status
CREATE INDEX IF NOT EXISTS idx_user_access_controls_status ON auth.user_access_controls (status);
