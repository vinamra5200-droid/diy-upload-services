package in.qualtechedge.qcp.templates.dto.response;

/**
 * §2.4 proceed response — {@code submission}/{@code job} are mutually exclusive, exactly one is
 * non-null depending on the template's {@code makerCheckerEnabled}.
 */
public record ProceedResponse(
        UploadAttemptResponse attempt,
        UploadSubmissionResponse submission,
        UploadJobResponse job
) {
}
