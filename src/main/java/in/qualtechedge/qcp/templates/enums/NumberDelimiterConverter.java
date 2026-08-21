package in.qualtechedge.qcp.templates.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link NumberDelimiter} using its wire {@link NumberDelimiter#value()}, not {@code name()}. */
@Converter(autoApply = true)
public class NumberDelimiterConverter implements AttributeConverter<NumberDelimiter, String> {

    @Override
    public String convertToDatabaseColumn(NumberDelimiter attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NumberDelimiter convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NumberDelimiter.fromValue(dbData);
    }
}
