-- V1_0_59__create_audit_event_catalogue.sql
-- Purpose: static reference table listing every audit_events.event_code this system is allowed to
-- record — both the admin config-mutation codes already in production use (admin-api-contract.md
-- §9/§12.6) and the full upload-pipeline event catalogue from the Solution Design (SD §12.3),
-- which nothing emits yet (see category = 'PIPELINE'; the pipeline stages that would emit most of
-- them — validation, checker review, job creation, queue push — have no real backend layer yet,
-- V1_0_52..54's comments). Cataloguing them now means the schema is ready the moment each stage is
-- built, and V1_0_60 adds a foreign key so audit_events can never contain an uncatalogued code.
--
-- S3_PENDING_WRITE_*/S3_PROMOTE_*/S3_WRITE_* suffixes are this migration's own choice: SD §12.3
-- allocates event numbers 17-19, 23-24, and 25 to these families but only spells out the "_*"
-- prefix, not the individual suffixes. STARTED/COMPLETED/FAILED (3), COMPLETED/FAILED (2), and
-- COMPLETED (1) were chosen to match that count and this codebase's existing UploadFileStatus
-- vocabulary (see UploadS3Worker) — revisit if the eventual upload-api-contract.md names them
-- differently.

CREATE TABLE audit_event_catalogue (
  event_code    TEXT PRIMARY KEY,
  category      TEXT NOT NULL CHECK (category IN ('ADMIN', 'PIPELINE')),
  description   TEXT NOT NULL,
  sd_reference  TEXT,

  CONSTRAINT audit_event_catalogue_code_not_blank CHECK (btrim(event_code) <> '')
);

COMMENT ON TABLE audit_event_catalogue IS
  'Static reference data — the complete set of event_code values audit_events is allowed to '
  'contain. Not admin-editable; changes ship as a new migration, same as the codes themselves.';

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  -- Admin config-mutation events — already recorded by every *ServiceImpl's maker-checker methods
  -- (admin-api-contract.md §9/§12.6). One row per event actually emitted in code today.
  ('ADMIN_ROLE_CREATED',          'ADMIN', 'Upload role created (draft)',                      'admin-api-contract.md §9'),
  ('ADMIN_ROLE_UPDATED',          'ADMIN', 'Upload role edited',                                'admin-api-contract.md §9'),
  ('ADMIN_ROLE_SUBMITTED',        'ADMIN', 'Upload role submitted for Checker Admin approval',  'admin-api-contract.md §9'),
  ('ADMIN_ROLE_ACTIVATED',        'ADMIN', 'Upload role approved and activated',                'admin-api-contract.md §9'),
  ('ADMIN_ROLE_REJECTED',         'ADMIN', 'Upload role rejected by Checker Admin',              'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_CREATED',      'ADMIN', 'Database connection created (draft)',                'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_UPDATED',      'ADMIN', 'Database connection edited',                         'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_SUBMITTED',    'ADMIN', 'Database connection submitted for approval',         'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_ACTIVATED',    'ADMIN', 'Database connection approved and activated',         'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_REJECTED',     'ADMIN', 'Database connection rejected by Checker Admin',      'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_CREATED',    'ADMIN', 'API configuration created (draft)',                  'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_UPDATED',    'ADMIN', 'API configuration edited',                           'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_SUBMITTED',  'ADMIN', 'API configuration submitted for approval',           'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_ACTIVATED',  'ADMIN', 'API configuration approved and activated',           'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_REJECTED',   'ADMIN', 'API configuration rejected by Checker Admin',        'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_CREATED',      'ADMIN', 'Template created (draft)',                           'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_UPDATED',      'ADMIN', 'Template edited',                                    'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_SUBMITTED',    'ADMIN', 'Template submitted for approval',                    'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_ACTIVATED',    'ADMIN', 'Template approved and activated',                    'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_REJECTED',     'ADMIN', 'Template rejected by Checker Admin',                 'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_CLONED',       'ADMIN', 'Template cloned into a new draft version',           'admin-api-contract.md §9'),
  ('ADMIN_USER_CREATED',          'ADMIN', 'Maker user created (draft)',                         'admin-api-contract.md §9'),
  ('ADMIN_USER_UPDATED',          'ADMIN', 'Maker user edited',                                  'admin-api-contract.md §9'),
  ('ADMIN_USER_SUBMITTED',        'ADMIN', 'Maker user submitted for approval',                  'admin-api-contract.md §9'),
  ('ADMIN_USER_ACTIVATED',        'ADMIN', 'Maker user approved and activated',                  'admin-api-contract.md §9'),
  ('ADMIN_USER_REJECTED',         'ADMIN', 'Maker user rejected by Checker Admin',               'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_CREATED',       'ADMIN', 'Process created (draft)',                            'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_UPDATED',       'ADMIN', 'Process edited',                                     'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_SUBMITTED',     'ADMIN', 'Process submitted for approval',                     'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_ACTIVATED',     'ADMIN', 'Process approved and activated',                     'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_REJECTED',      'ADMIN', 'Process rejected by Checker Admin',                  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_CREATED',       'ADMIN', 'Interim storage connection created (draft)',         'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_UPDATED',       'ADMIN', 'Interim storage connection edited',                  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_SUBMITTED',     'ADMIN', 'Interim storage connection submitted for approval',  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_ACTIVATED',     'ADMIN', 'Interim storage connection approved and activated',  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_REJECTED',      'ADMIN', 'Interim storage connection rejected by Checker Admin','admin-api-contract.md §9'),

  -- Upload-pipeline events — the full normative catalogue from SD §12.3, in event-number order.
  -- Only FILE_RECEIVED and FILE_REJECTED are emitted today (S3UploadServiceImpl, wired in this
  -- pass); everything else is catalogued ahead of the backend layer that will emit it.
  ('AUTH_OK',                     'PIPELINE', 'Maker/Checker authenticated',                            'SD §12.3 #1'),
  ('PROCESS_SELECTED',            'PIPELINE', 'Maker selected an upload process',                       'SD §12.3 #2'),
  ('TEMPLATE_SELECTED',           'PIPELINE', 'Active template resolved for the process',               'SD §12.3 #3'),
  ('TEMPLATE_DOWNLOADED',         'PIPELINE', 'Maker downloaded the blank template',                    'SD §12.3 #4'),
  ('FILE_RECEIVED',               'PIPELINE', 'Raw file accepted, SHA-256 checksum recorded',           'SD §12.3 #5'),
  ('FILE_REJECTED',               'PIPELINE', 'Raw file rejected (format/size/empty/duplicate checksum)','SD §12.3 #6'),
  ('CONCURRENT_UPLOAD_REJECTED',  'PIPELINE', 'Second concurrent upload on the same process rejected',  'SD §12.3 #6a'),
  ('VALIDATION_STARTED',          'PIPELINE', 'Chunked validation started',                              'SD §12.3 #8'),
  ('VALIDATION_CHUNK_DONE',       'PIPELINE', 'One validation chunk completed (optional, high-volume)',  'SD §12.3 #9'),
  ('VALIDATION_COMPLETED',        'PIPELINE', 'Validation run completed, results available',            'SD §12.3 #10'),
  ('VALIDATION_SKIPPED',          'PIPELINE', 'Validations skipped for this upload (audit reason)',     'SD §12.3 #11'),
  ('SESSION_TIMED_OUT',           'PIPELINE', 'Validation Service session timed out',                   'SD §12.3 #11a'),
  ('DECISION_REUPLOAD',           'PIPELINE', 'Maker chose to re-upload the entire file',               'SD §12.3 #15'),
  ('DECISION_PROCEED',            'PIPELINE', 'Maker chose to continue with successful records',        'SD §12.3 #16'),
  ('S3_PENDING_WRITE_STARTED',    'PIPELINE', 'Write to the pending/holding location started',          'SD §12.3 #17-19'),
  ('S3_PENDING_WRITE_COMPLETED',  'PIPELINE', 'Write to the pending/holding location completed',        'SD §12.3 #17-19'),
  ('S3_PENDING_WRITE_FAILED',     'PIPELINE', 'Write to the pending/holding location failed',           'SD §12.3 #17-19'),
  ('CHECKER_SUBMITTED',           'PIPELINE', 'Submission entered the Checker Batch Upload inbox',      'SD §12.3 #20'),
  ('CHECKER_APPROVED',            'PIPELINE', 'Checker accepted the submission',                        'SD §12.3 #21'),
  ('CHECKER_REJECTED',            'PIPELINE', 'Checker rejected the submission, or it expired',         'SD §12.3 #22'),
  ('S3_PROMOTE_COMPLETED',        'PIPELINE', 'Promoted pending -> final/completed location',           'SD §12.3 #23-24'),
  ('S3_PROMOTE_FAILED',           'PIPELINE', 'Promotion pending -> final/completed location failed',   'SD §12.3 #23-24'),
  ('S3_WRITE_COMPLETED',          'PIPELINE', 'Dual-control-off direct write to the completed location','SD §12.3 #25'),
  ('JOB_METADATA_CREATED',        'PIPELINE', 'Downstream job metadata row created',                    'SD §12.3 #26'),
  ('ENQUEUE_PUSHED',              'PIPELINE', 'Job pushed to the downstream queue',                      'SD §12.3 #27'),
  ('ENQUEUE_FAILED',              'PIPELINE', 'Push to the downstream queue failed',                     'SD §12.3 #28'),
  ('SESSION_FINALIZED',           'PIPELINE', 'Validation Service released this upload''s working data', 'SD §12.3 #29');
