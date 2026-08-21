package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tenant onboarding request (admin API). The short code becomes the subdomain segment,
 * the MDC/log-file key and part of the database name — lowercase alphanumeric only.
 */
public record TenantRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "shortCode must not be blank")
        @Pattern(regexp = "^[a-z][a-z0-9]{1,19}$",
                message = "shortCode must be 2-20 lowercase alphanumeric characters starting with a letter")
        String shortCode,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description
) {
}
