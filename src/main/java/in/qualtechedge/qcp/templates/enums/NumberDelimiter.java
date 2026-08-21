package in.qualtechedge.qcp.templates.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Thousands/decimal delimiter for {@code DECIMAL_PRECISION} validation rules. The wire/DB values
 * are punctuation ({@code .}, {@code ,}, {@code '}) and not legal Java identifiers, so this enum
 * carries an explicit {@link #value()} instead of relying on {@code name()}.
 */
public enum NumberDelimiter {
    DOT("."),
    COMMA(","),
    APOSTROPHE("'");

    private final String value;

    NumberDelimiter(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static NumberDelimiter fromValue(String value) {
        for (NumberDelimiter delimiter : values()) {
            if (delimiter.value.equals(value)) {
                return delimiter;
            }
        }
        throw new IllegalArgumentException("Unknown number delimiter: " + value);
    }
}
