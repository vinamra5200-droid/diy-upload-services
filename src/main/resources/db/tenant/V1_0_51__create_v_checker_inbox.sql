-- V1_0_51__create_v_checker_inbox.sql
-- Purpose: Checker inbox — derived view, not a table (admin-api-contract.md §8). Pending Maker
-- submissions across every governed entity. The service layer must hide rows where
-- submitted_by = current actor (four-eyes); process_id_ref is populated for templates only, to
-- build a review link back to their parent process.

CREATE OR REPLACE VIEW v_checker_inbox AS
SELECT
  'chg-process-' || p.process_id                          AS change_id,
  'process'                                                AS entity_type,
  p.process_id                                            AS entity_id,
  p.process_name                                          AS entity_label,
  'Process ' || p.process_id || ' awaiting Checker Admin' AS summary,
  coalesce(p.submitted_by, p.created_by)                  AS submitted_by,
  p.updated_at                                            AS submitted_at,
  TRUE                                                    AS actor_ne_submitter,
  NULL::TEXT                                              AS process_id_ref
FROM processes p
WHERE p.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-template-' || t.template_id,
  'template',
  t.template_id,
  t.template_code || ' v' || t.version,
  'Template ' || t.template_code || ' awaiting Checker Admin',
  coalesce(t.submitted_by, t.created_by),
  t.updated_at,
  TRUE,
  t.process_id
FROM templates t
WHERE t.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-role-' || r.role_id,
  'role',
  r.role_id,
  r.role_name,
  'Upload role ' || r.role_name || ' awaiting Checker Admin',
  coalesce(r.submitted_by, r.created_by),
  r.updated_at,
  TRUE,
  NULL
FROM upload_roles r
WHERE r.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-user-' || u.user_id,
  'user',
  u.user_id,
  u.full_name,
  'Maker user ' || u.username || ' awaiting Checker Admin',
  coalesce(u.submitted_by, u.created_by),
  u.updated_at,
  TRUE,
  NULL
FROM maker_users u
WHERE u.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-storage-' || s.config_id,
  'storage',
  s.config_id,
  s.connection_label,
  'Storage connection ' || s.connection_label || ' awaiting Checker Admin',
  coalesce(s.submitted_by, s.updated_by),
  s.updated_at,
  TRUE,
  NULL
FROM storage_configs s
WHERE s.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-database-' || d.connection_id,
  'database',
  d.connection_id,
  d.connection_label,
  'Database connection ' || d.connection_label || ' awaiting Checker Admin',
  coalesce(d.submitted_by, d.updated_by),
  d.updated_at,
  TRUE,
  NULL
FROM database_connections d
WHERE d.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-apiconfig-' || a.config_id,
  'apiConfig',
  a.config_id,
  a.label,
  'API configuration ' || a.label || ' awaiting Checker Admin',
  coalesce(a.submitted_by, a.updated_by),
  a.updated_at,
  TRUE,
  NULL
FROM api_configs a
WHERE a.status = 'waitingForChecker';

COMMENT ON VIEW v_checker_inbox IS
  'Pending Maker submissions across all governed entities. API must hide rows where submitted_by = current actor (four-eyes). process_id_ref is populated for templates only, to build a review link back to their parent process.';
