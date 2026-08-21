package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UploadRoleRequest(
        @NotBlank(message = "roleName must not be blank")
        @Size(max = 64, message = "roleName must be at most 64 characters")
        String roleName,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @NotNull(message = "processAccess must not be null")
        List<String> processAccess,

        @NotNull(message = "isActive must not be null")
        Boolean isActive
) {
}
