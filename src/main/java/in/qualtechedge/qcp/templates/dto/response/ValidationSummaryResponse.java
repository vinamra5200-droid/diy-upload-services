package in.qualtechedge.qcp.templates.dto.response;

/** {@code ValidationSummary} shape (upload-api-contract.md §5). */
public record ValidationSummaryResponse(
        int totalRecords,
        int passedRecords,
        int failedRecords
) {
}
