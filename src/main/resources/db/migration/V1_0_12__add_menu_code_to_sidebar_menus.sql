-- V1_0_12__add_menu_code_to_sidebar_menus.sql
-- Purpose: The two columns the sidebar is actually addressed by.
--
-- V1_0_8 created auth.sidebar_menus with a title and a path but no stable key and no grouping,
-- so a frontend could only match a menu by its display text — which is the one field a person
-- is allowed to change. menu_code is that key; section_code is the heading a menu sits under.
--
-- A separate migration rather than an edit to V1_0_8: that one has already run everywhere a
-- service built from this template is deployed, and an applied migration is immutable.
-- ============================================================================

ALTER TABLE auth.sidebar_menus
    ADD COLUMN IF NOT EXISTS menu_code    VARCHAR(80),
    ADD COLUMN IF NOT EXISTS section_code VARCHAR(60);

COMMENT ON COLUMN auth.sidebar_menus.menu_code IS 'Stable key the frontend addresses this menu by — never the title';
COMMENT ON COLUMN auth.sidebar_menus.section_code IS 'Sidebar heading this menu is grouped under';

-- Unique where present. Menus seeded before this migration have none, and a partial index lets
-- them stay that way rather than forcing a backfill of codes nobody has chosen yet.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sidebar_menus_menu_code
    ON auth.sidebar_menus (menu_code)
    WHERE menu_code IS NOT NULL;
