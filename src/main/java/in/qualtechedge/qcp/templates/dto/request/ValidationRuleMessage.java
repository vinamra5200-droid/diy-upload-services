package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.NumberDelimiter;
import in.qualtechedge.qcp.templates.enums.SumCompareOperator;
import in.qualtechedge.qcp.templates.enums.ValidationRuleType;
import in.qualtechedge.qcp.templates.enums.ValidationSeverity;
import java.math.BigDecimal;
import java.util.List;

/**
 * One {@code template_validation_rules} row, embedded on the Kafka wire. Sent only on the first
 * chunk of a batch ({@link BatchChunkMessage#rules()}, {@code chunkSequence == 0}) — the rule set
 * is snapshotted once per batch rather than repeated on every chunk. Field-for-field mirror of
 * validation-service's own {@code ValidationRuleMessage}, and of this repo's own
 * {@code ValidationRuleResponse} (same shape, minus {@code ruleId} — not needed to validate a row).
 */
public record ValidationRuleMessage(
        String field,
        ValidationRuleType type,
        ValidationSeverity severity,
        String message,

        String profile,
        String pattern,
        String format,

        Boolean required,
        Boolean rejectEmptyString,
        Boolean rejectWhitespace,

        List<String> allowedValues,
        Boolean caseInsensitive,

        Integer decimalPlaces,
        NumberDelimiter delimiter,
        BigDecimal minValue,
        BigDecimal maxValue,

        String expression,
        List<FormulaTerm> formulaTerms,
        List<String> formulaOperators,

        SumCompareOperator compareOperator,
        String groupByField,
        TransactionSplit transactionSplit,

        Condition condition
) {

    public record FormulaTerm(String kind, String field, BigDecimal value, Boolean isPercent) {
    }

    public record TransactionSplit(
            String mode,
            String splitField, String branchAValue, String branchBValue, String amountField,
            String branchAField, String branchBField) {
    }

    public record Condition(String conditionField, String conditionOperator, String conditionValue) {
    }
}
