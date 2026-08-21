package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.NumberDelimiter;
import in.qualtechedge.qcp.templates.enums.SumCompareOperator;
import in.qualtechedge.qcp.templates.enums.ValidationRuleType;
import in.qualtechedge.qcp.templates.enums.ValidationSeverity;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code ruleId} is deliberately absent — templates are saved as a full replace of every child
 * collection (TemplateServiceImpl deletes and reinserts on every update), so rule ids are always
 * freshly generated server-side rather than round-tripped from the client.
 */
public record ValidationRuleRequest(
        String field,

        @NotNull(message = "rules[].type must not be null")
        ValidationRuleType type,

        @NotNull(message = "rules[].severity must not be null")
        ValidationSeverity severity,

        String message,

        // FORMAT_REGEX
        String profile,
        String pattern,
        String format,

        // NULL_EMPTY
        Boolean required,
        Boolean rejectEmptyString,
        Boolean rejectWhitespace,

        // ENUM
        List<String> allowedValues,
        Boolean caseInsensitive,

        // DECIMAL_PRECISION / RANGE
        Integer decimalPlaces,
        NumberDelimiter delimiter,
        BigDecimal minValue,
        BigDecimal maxValue,

        // FUNCTIONAL
        String expression,
        List<FormulaTerm> formulaTerms,
        List<String> formulaOperators,

        // TRANSACTION
        SumCompareOperator compareOperator,
        String groupByField,
        TransactionSplit transactionSplit,

        // Shared optional row filter (FUNCTIONAL / TRANSACTION)
        Condition condition
) {

    public record FormulaTerm(String kind, String field, BigDecimal value, Boolean isPercent) {
    }

    public record TransactionSplit(String splitField, String branchAValue, String branchBValue, String amountField) {
    }

    public record Condition(String conditionField, String conditionOperator, String conditionValue) {
    }
}
