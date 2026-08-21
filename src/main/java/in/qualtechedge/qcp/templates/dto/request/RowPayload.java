package in.qualtechedge.qcp.templates.dto.request;

import java.util.Map;

/**
 * One row inside a {@link BatchChunkMessage} — field-for-field mirror of validation-service's
 * {@code RowPayload} (its consumer, {@code BatchChunkListener}, deserializes straight into that
 * type, so the JSON shape here must match exactly).
 */
public record RowPayload(
        Integer rowNumber,
        Map<String, Object> data
) {
}
