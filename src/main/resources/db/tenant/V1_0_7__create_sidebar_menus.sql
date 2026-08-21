-- V1_0_7__create_sidebar_menus.sql  (TENANT database)
-- Purpose: A tenant's own sidebar navigation.
--
-- Why this exists when auth.sidebar_menus already does: they are different sets of rows, not one
-- set filtered two ways. The system database's menus are the administrator's console, and those
-- screens are backed by tables a tenant database does not have — serving them on a tenant host
-- would produce a sidebar whose every entry fails. A tenant reads this table and nothing else.
-- ============================================================================

CREATE TABLE IF NOT EXISTS sidebar_menus (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_type INTEGER,          -- 1=Menu, 2=Sub Menu, 3=Sub Menu Item, 4..6=deeper nesting
    parent_id UUID,             -- Self-reference for hierarchy (nullable for root menus)
    menu_code VARCHAR(80),      -- Stable key the frontend addresses this menu by — never the title
    title VARCHAR(100) NOT NULL,
    description TEXT,
    path VARCHAR(255),          -- Route path
    icon VARCHAR(100),          -- Icon key resolved by the frontend
    order_index INTEGER,        -- Sort order within parent
    section_code VARCHAR(60),   -- Sidebar heading this menu is grouped under
    status INTEGER NOT NULL,    -- 1=active, 0=inactive
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_sidebar_menus_menu_code UNIQUE (menu_code),
    CONSTRAINT fk_sidebar_menu_parent FOREIGN KEY (parent_id)
        REFERENCES sidebar_menus(id) ON DELETE CASCADE,
    CONSTRAINT fk_sidebar_menu_created_by FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_sidebar_menu_updated_by FOREIGN KEY (updated_by)
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_sidebar_menus_parent_id ON sidebar_menus (parent_id);
CREATE INDEX IF NOT EXISTS idx_sidebar_menus_order ON sidebar_menus (parent_id, order_index);
CREATE INDEX IF NOT EXISTS idx_sidebar_menus_status ON sidebar_menus (status);
