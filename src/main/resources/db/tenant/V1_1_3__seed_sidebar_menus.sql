-- V1_1_3__seed_sidebar_menus.sql  (TENANT database)
-- Purpose: A tenant's sidebar on the day their database is provisioned.
--
-- Deliberately smaller than the administrator's: a tenant sees their own workspace, not the
-- screens that manage every tenant. Same ids as the system seed would be wrong — these are
-- different rows in a different database, and nothing joins them.
-- ============================================================================

INSERT INTO sidebar_menus (
    id, menu_type, parent_id, menu_code, title, description,
    path, icon, order_index, section_code, status, created_by, created_at
) VALUES
    ('c2e4f3d5-6b7c-4d8e-9f0a-1b2c3d4e5f01', 1, NULL, 'dashboard', 'Dashboard',
     'Landing page after sign-in', '/dashboard', 'dashboard', 1, 'main', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP),
    ('c2e4f3d5-6b7c-4d8e-9f0a-1b2c3d4e5f02', 1, NULL, 'examples', 'Examples',
     'Reference screens shipped with the template', '/examples', 'examples', 2, 'main', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
