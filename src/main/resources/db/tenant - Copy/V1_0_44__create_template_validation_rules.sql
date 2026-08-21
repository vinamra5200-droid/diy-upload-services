-- V1_0_44__create_template_validation_rules.sql
-- Purpose: Pre-upload / functional / master / transactional validation rules
-- (admin-api-contract.md §2.4 "Validation Rule Object").

CREATE TABLE template_validation_rules (
  rule_id               TEXT PRIMARY KEY DEFAULT generate_id('R'),
  template_id           TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  field                 TEXT NOT NULL DEFAULT '',
  rule_type             VARCHAR(20) NOT NULL CHECK (rule_type IN
                         ('FORMAT_REGEX','NULL_EMPTY','ENUM','DECIMAL_PRECISION','RANGE','DATE_FORMAT','FUNCTIONAL','MASTER_DATA','TRANSACTION')),
  severity              VARCHAR(10) NOT NULL DEFAULT 'ERROR' CHECK (severity IN ('ERROR','WARNING')),
  message               TEXT NOT NULL DEFAULT '',

  -- FORMAT_REGEX
  profile               TEXT,
  pattern               TEXT,
  format                TEXT,

  -- NULL_EMPTY
  required              BOOLEAN,
  reject_empty_string   BOOLEAN,
  reject_whitespace     BOOLEAN,

  -- ENUM
  allowed_values        TEXT[],
  case_insensitive      BOOLEAN,

  -- DECIMAL_PRECISION / RANGE
  decimal_places        INTEGER,
  delimiter             VARCHAR(1) CHECK (delimiter IS NULL OR delimiter IN ('.', ',', '''')),
  min_value              NUMERIC,
  max_value              NUMERIC,

  -- FUNCTIONAL
  expression             TEXT,
  formula_terms          JSONB,
  formula_operators      JSONB,

  -- TRANSACTION
  compare_operator        VARCHAR(4) CHECK (compare_operator IS NULL OR compare_operator IN ('lt','lte','gt','gte','eq')),
  group_by_field          TEXT,
  transaction_split       JSONB,

  -- Shared optional row filter (FUNCTIONAL / TRANSACTION)
  condition               JSONB,

  sort_order              INTEGER NOT NULL DEFAULT 0,

  CONSTRAINT template_rules_formula_terms_is_array CHECK (formula_terms IS NULL OR jsonb_typeof(formula_terms) = 'array'),
  CONSTRAINT template_rules_formula_operators_is_array CHECK (formula_operators IS NULL OR jsonb_typeof(formula_operators) = 'array'),
  CONSTRAINT template_rules_transaction_split_is_object CHECK (transaction_split IS NULL OR jsonb_typeof(transaction_split) = 'object'),
  CONSTRAINT template_rules_condition_is_object CHECK (condition IS NULL OR jsonb_typeof(condition) = 'object')
);

CREATE INDEX template_rules_template_idx ON template_validation_rules (template_id, sort_order);
CREATE INDEX template_rules_type_idx ON template_validation_rules (template_id, rule_type);

COMMENT ON TABLE template_validation_rules IS 'Optional validation rules. MASTER_DATA is reserved (coming soon; cannot be activated).';
COMMENT ON COLUMN template_validation_rules.transaction_split IS
  'TRANSACTION rules only: {splitField, branchAValue, branchBValue, amountField} — splits filtered/grouped rows into two categories and compares their summed amountField using compare_operator.';
COMMENT ON COLUMN template_validation_rules.condition IS
  'Optional row filter: {conditionField, conditionOperator, conditionValue}. conditionOperator in (equals, notEquals, in, notIn, empty, notEmpty).';
COMMENT ON COLUMN template_validation_rules.formula_terms IS
  'FUNCTIONAL formula_compare rules only: array of {kind:"field",field} | {kind:"constant",value,isPercent?}.';
COMMENT ON COLUMN template_validation_rules.formula_operators IS
  'FUNCTIONAL formula_compare rules only: array of add|subtract|multiply|divide, one fewer than formula_terms.';
