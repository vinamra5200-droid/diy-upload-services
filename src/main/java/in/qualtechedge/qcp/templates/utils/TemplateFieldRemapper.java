package in.qualtechedge.qcp.templates.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remaps a row keyed by the uploaded file's literal header text ({@code source_column}) to the
 * template field's {@code target_field}, so downstream Kafka consumers see the names they expect
 * rather than whatever the maker's spreadsheet happened to call a column. Columns with no field
 * mapping are dropped. Shared by {@link in.qualtechedge.qcp.templates.service.impl.BatchChunkPublisherImpl}
 * and {@link in.qualtechedge.qcp.templates.service.impl.PostLoadActionDispatcherImpl} — both read
 * an interim-storage file that still has source-column headers and publish target-field-keyed rows.
 */
public final class TemplateFieldRemapper {

    private TemplateFieldRemapper() {
    }

    public static Map<String, Object> remap(Map<String, Object> sourceKeyedData, Map<String, String> sourceToTargetField) {
        Map<String, Object> remapped = new LinkedHashMap<>();
        sourceKeyedData.forEach((sourceColumn, value) -> {
            String targetField = sourceToTargetField.get(sourceColumn);
            if (targetField != null) {
                remapped.put(targetField, value);
            }
        });
        return remapped;
    }
}
