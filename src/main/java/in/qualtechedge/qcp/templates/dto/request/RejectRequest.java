package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Shared reject-endpoint body across every governed resource (admin-api-contract.md §1.5 etc). */
public record RejectRequest(
        @NotBlank(message = "reason must not be blank")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {
}
