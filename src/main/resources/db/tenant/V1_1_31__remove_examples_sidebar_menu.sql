-- V1_1_31__remove_examples_sidebar_menu.sql  (TENANT database)
-- Purpose: The example feature (V1_0_30/V1_1_30) was replaced by the real DIY Upload Admin
-- feature — its "examples" sidebar entry (V1_1_3) now points at a route with no backend.
-- Migrations are forward-only, so this removes the row rather than editing V1_1_3 in place.

DELETE FROM sidebar_menus WHERE menu_code = 'examples';
