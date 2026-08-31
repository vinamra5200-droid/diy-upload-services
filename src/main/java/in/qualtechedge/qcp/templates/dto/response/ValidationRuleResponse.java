package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.NumberDelimiter;
import in.qualtechedge.qcp.templates.enums.SumCompareOperator;
import in.qualtechedge.qcp.templates.enums.ValidationRuleType;
import in.qualtechedge.qcp.templates.enums.ValidationSeverity;
import java.math.BigDecimal;
import java.util.List;

public record ValidationRuleResponse(
        String ruleId,
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
