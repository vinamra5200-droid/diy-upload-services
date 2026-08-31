package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Full replace — every field required except {@code schedule} (admin-api-contract.md §2.4). */
public record UpdateTemplateRequest(
        @NotBlank(message = "templateName must not be blank")
        String templateName,

        @NotNull(message = "templateDescription must not be null")
        String templateDescription,

        @NotNull(message = "fields must not be null")
        List<TemplateFieldRequest> fields,

        @NotNull(message = "uploadFormats must not be null")
        UploadFormatsRequest uploadFormats,

        @NotNull(message = "packageGate must not be null")
        PackageGateRequest packageGate,

        @NotNull(message = "postLoadAction must not be null")
        PostLoadActionRequest postLoadAction,

        @NotNull(message = "makerChecker must not be null")
        MakerCheckerRequest makerChecker,

        @NotNull(message = "transformations must not be null")
        List<TransformationRequest> transformations,

        @NotNull(message = "validationsEnabled must not be null")
        Boolean validationsEnabled,

        @NotNull(message = "rules must not be null")
        List<ValidationRuleRequest> rules,

        @NotNull(message = "failFast must not be null")
        Boolean failFast,

        /** Nullable — the only optional field on this contract. */
        ScheduleRequest schedule
) {
}
