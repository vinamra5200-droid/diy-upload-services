package in.qualtechedge.qcp.templates.dto.response;

import java.util.List;

public record MakerCheckerResponse(
        boolean enabled,
        List<String> checkerRoles,
        boolean actorNeSubmitter,
        int slaHours,
        String escalateToRole
) {
}
