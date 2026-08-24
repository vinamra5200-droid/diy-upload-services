package in.qualtechedge.qcp.templates.dto.response;

/** §4.3 accept response. */
public record AcceptSubmissionResponse(
        UploadSubmissionResponse submission,
        UploadJobResponse job
) {
}
