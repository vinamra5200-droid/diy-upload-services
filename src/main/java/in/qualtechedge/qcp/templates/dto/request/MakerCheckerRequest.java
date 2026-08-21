package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MakerCheckerRequest(
        boolean enabled,

        @NotNull(message = "makerChecker.checkerRoles must not be null")
        List<String> checkerRoles,

        boolean actorNeSubmitter,
        int slaHours,
        String escalateToRole
) {
}
