-- transaction_split now also carries a "differentColumns" mode (branchAField/branchBField summed
-- directly, no category matching) alongside the original "sameColumn" shape (splitField/
-- branchAValue/branchBValue/amountField). Still JSONB, no column/constraint change needed —
-- this only brings the column comment up to date with the actual shape now written/read by
-- ValidationRuleRequest.TransactionSplit / ValidationRuleMessage.TransactionSplit /
-- ValidationRuleResponse.TransactionSplit.
COMMENT ON COLUMN template_validation_rules.transaction_split IS
  'TRANSACTION rules only: {mode, splitField, branchAValue, branchBValue, amountField, branchAField, branchBField}. mode "sameColumn" (default/null): splits rows into two categories via splitField vs branchAValue/branchBValue, sums amountField per category. mode "differentColumns": branchAField/branchBField are already-separate amount columns, summed directly with no category matching. Either way, the two branch sums are compared using compare_operator.';
