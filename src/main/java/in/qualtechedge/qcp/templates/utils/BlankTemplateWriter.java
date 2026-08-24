package in.qualtechedge.qcp.templates.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Writes an empty (headers-only) template file for {@code GET
 * /processes/:processId/blank-template} (upload-api-contract.md §1.3) — the reverse of
 * {@link UploadFileRowReader}: headers come from {@code template.fields[].sourceColumn}, in
 * {@code sortOrder}, and no data rows are written.
 */
public final class BlankTemplateWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private BlankTemplateWriter() {
    }

    public static byte[] writeXlsx(List<String> headers, String sheetName) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isBlank() ? "Sheet1" : sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write blank xlsx template", e);
        }
    }

    public static byte[] writeCsv(List<String> headers) {
        String line = String.join(",", headers.stream().map(BlankTemplateWriter::csvEscape).toList());
        return (line + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /** An empty array, wrapped at {@code rootArrayPath} when the template's JSON format names one. */
    public static byte[] writeJson(String rootArrayPath) {
        try {
            if (rootArrayPath == null || rootArrayPath.isBlank()) {
                return JSON_MAPPER.writeValueAsBytes(List.of());
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put(rootArrayPath, List.of());
            return JSON_MAPPER.writeValueAsBytes(wrapper);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write blank json template", e);
        }
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
