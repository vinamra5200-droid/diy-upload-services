-- V1_1_5__remove_examples_sidebar_menu.sql  (SYSTEM database)
-- Purpose: The example feature (V1_0_30/V1_1_30 in db/tenant) was replaced by the real DIY
-- Upload Admin feature — its "examples" sidebar entry (V1_1_4) now points at a route with no
-- backend. Migrations are forward-only, so this removes the row rather than editing V1_1_4 in
-- place.

DELETE FROM auth.sidebar_menus WHERE menu_code = 'examples';
