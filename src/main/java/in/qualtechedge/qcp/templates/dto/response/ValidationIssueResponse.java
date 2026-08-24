package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ValidationSeverity;

/** {@code ValidationIssue} shape (upload-api-contract.md §5). */
public record ValidationIssueResponse(
        int rowNumber,
        String field,
        ValidationSeverity severity,
        String ruleId,
        String ruleType,
        String actualValue,
        String expected,
        String message
) {
}
