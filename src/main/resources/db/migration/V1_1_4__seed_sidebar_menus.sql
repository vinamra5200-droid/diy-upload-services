-- V1_1_4__seed_sidebar_menus.sql
-- Purpose: The administrator's sidebar, so a new service has navigation on first run.
--
-- Two entries, matching what the frontend template ships as routes. Replace them with the real
-- sections of the service being built — this table is the source of truth for the sidebar, and
-- GET /api/v1/menus is what the frontend reads. Adding a screen means adding a row here, not
-- editing a list in the frontend.
--
-- menu_code is what the frontend matches on to attach an icon; the title is display text and may
-- change without breaking anything.
-- ============================================================================

INSERT INTO auth.sidebar_menus (
    id, menu_type, parent_id, menu_code, title, description,
    path, icon, order_index, section_code, status, created_by, created_at
) VALUES
    ('b1f3d2c4-5a6b-4c7d-8e9f-0a1b2c3d4e01', 1, NULL, 'dashboard', 'Dashboard',
     'Landing page after sign-in', '/dashboard', 'dashboard', 1, 'main', 1,
     '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP),
    ('b1f3d2c4-5a6b-4c7d-8e9f-0a1b2c3d4e02', 1, NULL, 'examples', 'Examples',
     'Reference screens shipped with the template', '/examples', 'examples', 2, 'main', 1,
     '2cf9ea69-045a-4fc6-82d3-a77f7c0de70a', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
