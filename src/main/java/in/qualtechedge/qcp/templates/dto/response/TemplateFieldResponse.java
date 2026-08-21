package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.FieldType;

public record TemplateFieldResponse(
        String sourceColumn,
        String targetField,
        String fieldLabel,
        FieldType fieldType,
        boolean required
) {
}
