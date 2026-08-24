package in.qualtechedge.qcp.templates.dto.response;

/** §1.3 blank-template download — raw bytes plus the headers the controller streams them under. */
public record BlankTemplateFileResponse(
        byte[] content,
        String contentType,
        String filename
) {
}
