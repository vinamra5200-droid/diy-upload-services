package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateFieldRequest(
        @NotBlank(message = "sourceColumn must not be blank")
        String sourceColumn,

        @NotBlank(message = "targetField must not be blank")
        String targetField,

        @NotBlank(message = "fieldLabel must not be blank")
        String fieldLabel,

        @NotNull(message = "fieldType must not be null")
        FieldType fieldType,

        boolean required
) {
}
