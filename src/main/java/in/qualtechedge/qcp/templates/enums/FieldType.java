package in.qualtechedge.qcp.templates.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Column-to-field mapping data type ({@code template_fields.field_type}).
 * The wire/DB value "boolean" collides with the Java keyword, so this enum (unlike its siblings
 * in this package) carries an explicit {@link #value()} instead of relying on {@code name()}.
 */
public enum FieldType {
    STRING("string"),
    NUMBER("number"),
    DATE("date"),
    BOOL("boolean");

    private final String value;

    FieldType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static FieldType fromValue(String value) {
        for (FieldType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + value);
    }
}
