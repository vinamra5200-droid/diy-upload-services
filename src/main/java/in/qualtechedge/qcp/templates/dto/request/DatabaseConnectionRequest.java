package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.DatabaseProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DatabaseConnectionRequest(
        @NotNull(message = "provider must not be null")
        DatabaseProvider provider,

        @NotBlank(message = "connectionLabel must not be blank")
        @Size(max = 120, message = "connectionLabel must be at most 120 characters")
        String connectionLabel,

        @NotBlank(message = "connectionRef must not be blank")
        @Size(max = 500, message = "connectionRef must be at most 500 characters")
        String connectionRef,

        @NotNull(message = "tableNames must not be null")
        List<String> tableNames
) {
}
