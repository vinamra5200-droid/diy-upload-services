package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.NumberDelimiter;
import in.qualtechedge.qcp.templates.enums.SumCompareOperator;
import in.qualtechedge.qcp.templates.enums.ValidationRuleType;
import in.qualtechedge.qcp.templates.enums.ValidationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Validation rule ({@code template_validation_rules}) (admin-api-contract.md §2.4 "Validation
 * Rule Object"). {@code formulaTerms}/{@code formulaOperators}/{@code transactionSplit}/
 * {@code condition} are JSONB columns kept as raw JSON text, converted in
 * {@link in.qualtechedge.qcp.templates.mapper.TemplateMapper}.
 */
@Entity
@Table(name = "template_validation_rules")
@Getter
@Setter
@NoArgsConstructor
public class TemplateValidationRule {

    @Id
    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String field = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private ValidationRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValidationSeverity severity = ValidationSeverity.ERROR;

    @Column(nullable = false, columnDefinition = "text")
    private String message = "";

    // FORMAT_REGEX
    private String profile;
    private String pattern;
    private String format;

    // NULL_EMPTY
    private Boolean required;
    @Column(name = "reject_empty_string")
    private Boolean rejectEmptyString;
    @Column(name = "reject_whitespace")
    private Boolean rejectWhitespace;

    // ENUM
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_values")
    private String[] allowedValues;
    @Column(name = "case_insensitive")
    private Boolean caseInsensitive;

    // DECIMAL_PRECISION / RANGE
    @Column(name = "decimal_places")
    private Integer decimalPlaces;
    /** Persisted via {@link in.qualtechedge.qcp.templates.enums.NumberDelimiterConverter}
     * (autoApply) — no {@code @Enumerated} here, it would take precedence over the converter
     * and write the Java constant name ("DOT") instead of the wire/DB value ("."), which the
     * check constraint on this column rejects. */
    private NumberDelimiter delimiter;
    @Column(name = "min_value")
    private BigDecimal minValue;
    @Column(name = "max_value")
    private BigDecimal maxValue;

    // FUNCTIONAL
    private String expression;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formula_terms", columnDefinition = "jsonb")
    private String formulaTerms;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formula_operators", columnDefinition = "jsonb")
    private String formulaOperators;

    // TRANSACTION
    @Enumerated(EnumType.STRING)
    @Column(name = "compare_operator")
    private SumCompareOperator compareOperator;
    @Column(name = "group_by_field")
    private String groupByField;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_split", columnDefinition = "jsonb")
    private String transactionSplit;

    // Shared optional row filter (FUNCTIONAL / TRANSACTION)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String condition;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
