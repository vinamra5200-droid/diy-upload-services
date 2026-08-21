package in.qualtechedge.qcp.templates.enums;

/** Validation rule discriminator ({@code template_validation_rules.rule_type}). */
public enum ValidationRuleType {
    FORMAT_REGEX,
    NULL_EMPTY,
    ENUM,
    DECIMAL_PRECISION,
    RANGE,
    DATE_FORMAT,
    FUNCTIONAL,
    /** Reserved — coming soon. Blocks template submit/accept when present (contract §2.5/§2.6). */
    MASTER_DATA,
    TRANSACTION
}
