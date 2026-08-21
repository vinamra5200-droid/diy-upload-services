package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CloneTemplateRequest(
        @NotBlank(message = "newName must not be blank")
        String newName
) {
}
