package in.qualtechedge.qcp.templates.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Converts between JSONB column text and typed request/response records for entities whose
 * schema stores structured data as JSON ({@code api_configs}, {@code template_transformations},
 * {@code template_validation_rules}, {@code templates.schedule},
 * {@code template_version_snapshots}). Uses its own Jackson 2 {@link ObjectMapper} rather than
 * the Spring-managed bean, which is Jackson 3 (AGENTS.md rule 23) and a different type entirely.
 * <p>
 * {@code template_version_snapshots} stores a full {@code TemplateResponse}, which carries
 * {@code OffsetDateTime} fields (e.g. {@code createdAt}) — {@link JavaTimeModule} is required
 * for those to serialize at all (bare {@link ObjectMapper} throws {@code InvalidDefinitionException}
 * for Java 8 date/time types), and disabling {@code WRITE_DATES_AS_TIMESTAMPS} keeps the stored
 * JSON as ISO-8601 strings rather than numeric timestamp arrays.
 * <p>
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} is disabled because these columns hold long-lived rows: a
 * response DTO's shape can change (a field removed, e.g. {@code ValidationSummaryResponse}
 * dropping {@code warningRecords}) while older rows still carry the old shape in their JSONB.
 * Without this, reading one legacy row throws and can take down an entire list endpoint.
 */
public final class JsonColumnMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonColumnMapper() {
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize value to JSON", e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse JSON column", e);
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse JSON column", e);
        }
    }
}
