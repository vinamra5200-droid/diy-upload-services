package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProcessRequest(
        @NotBlank(message = "processName must not be blank")
        @Size(max = 120, message = "processName must be at most 120 characters")
        String processName,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {
}
