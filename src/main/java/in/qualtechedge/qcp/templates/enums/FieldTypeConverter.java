package in.qualtechedge.qcp.templates.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link FieldType} using its wire {@link FieldType#value()}, not {@code name()}. */
@Converter(autoApply = true)
public class FieldTypeConverter implements AttributeConverter<FieldType, String> {

    @Override
    public String convertToDatabaseColumn(FieldType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public FieldType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FieldType.fromValue(dbData);
    }
}
