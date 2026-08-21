-- V1_0_8__create_sidebar_menus.sql
-- Purpose: Hierarchical navigation menu configuration for the admin UI.

-- =============================================
-- Sidebar menus table for navigation structure
-- =============================================

-- Hierarchical menu structure for application navigation
CREATE TABLE IF NOT EXISTS auth.sidebar_menus (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_type INTEGER,         -- 1=Menu, 2=Sub Menu, 3=Sub Menu Item, 4=Sub-Sub Menu, 5=Sub-Sub Menu Item, 6=Sub-Sub-Sub Menu Item
    parent_id UUID,            -- Self-reference for hierarchy (nullable for root menus)
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    path VARCHAR(255) UNIQUE,  -- Route path (unique for navigation)
    icon VARCHAR(100),         -- Icon class/name for display
    order_index INTEGER,       -- Sort order within parent
    status INTEGER NOT NULL,   -- 1=active, 0=inactive
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,

    -- Self-referencing FK for parent menu
    CONSTRAINT fk_sidebar_menu_parent FOREIGN KEY (parent_id)
        REFERENCES auth.sidebar_menus(id) ON DELETE CASCADE,
    -- Creator reference
    CONSTRAINT fk_sidebar_menu_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users(id) ON DELETE SET NULL,
    -- Updater reference
    CONSTRAINT fk_sidebar_menu_updated_by FOREIGN KEY (updated_by)
        REFERENCES auth.users(id) ON DELETE SET NULL
);

-- Speed up lookups by parent for tree traversal
CREATE INDEX IF NOT EXISTS idx_sidebar_menus_parent_id ON auth.sidebar_menus (parent_id);

-- Speed up ordering within parent
CREATE INDEX IF NOT EXISTS idx_sidebar_menus_order ON auth.sidebar_menus (parent_id, order_index);

-- Speed up filtering by status
CREATE INDEX IF NOT EXISTS idx_sidebar_menus_status ON auth.sidebar_menus (status);
