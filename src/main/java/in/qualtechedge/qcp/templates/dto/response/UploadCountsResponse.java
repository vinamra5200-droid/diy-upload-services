package in.qualtechedge.qcp.templates.dto.response;

/**
 * Upload status counts for one process (dashboard use), optionally narrowed to a single
 * template within it. {@code completed}/{@code failed} are all-time totals ("till date" /
 * "in the past"); {@code inProgress}/{@code pending} reflect current state.
 */
public record UploadCountsResponse(
        String processId,
        String templateId,
        long pending,
        long inProgress,
        long completed,
        long failed,
        long total
) {
}
