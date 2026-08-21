package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTemplateRequest(
        @NotBlank(message = "templateName must not be blank")
        @Size(max = 120, message = "templateName must be at most 120 characters")
        String templateName,

        String templateDescription,

        Boolean makerCheckerEnabled
) {
}
