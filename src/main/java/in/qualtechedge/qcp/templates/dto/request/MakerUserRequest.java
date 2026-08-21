package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MakerUserRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 120, message = "username must be at most 120 characters")
        String username,

        @NotBlank(message = "fullName must not be blank")
        @Size(max = 120, message = "fullName must be at most 120 characters")
        String fullName,

        @NotNull(message = "roleIds must not be null")
        List<String> roleIds,

        @NotNull(message = "isActive must not be null")
        Boolean isActive
) {
}
