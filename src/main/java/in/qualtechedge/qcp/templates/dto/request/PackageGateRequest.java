package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotNull;

public record PackageGateRequest(
        @NotNull(message = "packageGate.maxSizeMb must not be null")
        Integer maxSizeMb,

        /** Nullable = unlimited. */
        Integer maxRows
) {
}
