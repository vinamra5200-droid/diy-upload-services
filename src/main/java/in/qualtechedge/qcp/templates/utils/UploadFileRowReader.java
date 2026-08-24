package in.qualtechedge.qcp.templates.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.monitorjbl.xlsx.StreamingReader;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Row-by-row reads of an uploaded file for {@link in.qualtechedge.qcp.templates.service.impl.BatchChunkPublisherImpl}
 * — bounded memory regardless of file size. xlsx uses {@link StreamingReader} (SAX-based under
 * the hood, wrapped behind normal POI {@code Row}/{@code Cell} iteration) rather than a
 * whole-workbook {@code XSSFWorkbook}; csv and json use Jackson's streaming APIs rather than
 * reading the file into one {@code String}/{@code List}.
 * <p>
 * xlsx/csv treat the first row as a header naming the columns of every following row's
 * {@code Map<String, Object>}; json expects a top-level array of row objects, each already a
 * {@code Map<String, Object>} of column name to value — there is no separate header row to skip.
 * <p>
 * Uses its own Jackson 2 types ({@code com.fasterxml.jackson.*}), not the Spring-autoconfigured
 * Jackson 3 {@code ObjectMapper} — same reasoning as {@link JsonColumnMapper}.
 */
public final class UploadFileRowReader {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<>() { };

    private UploadFileRowReader() {
    }

    @FunctionalInterface
    public interface RowConsumer {
        void accept(int rowNumber, Map<String, Object> data) throws IOException;
    }

    /** Maps a filename's extension to the format its rows should be parsed as. */
    public static UploadFormatKey detectFormat(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return UploadFormatKey.xlsx;
        }
        if (lower.endsWith(".csv")) {
            return UploadFormatKey.csv;
        }
        if (lower.endsWith(".json")) {
            return UploadFormatKey.json;
        }
        throw new IllegalArgumentException("Cannot determine an upload format for filename: " + filename);
    }

    public static void readRows(Path file, UploadFormatKey format, RowConsumer consumer) throws IOException {
        switch (format) {
            case xlsx -> readXlsx(file, consumer);
            case csv -> readCsv(file, consumer);
            case json -> readJson(file, consumer);
        }
    }

    private static void readXlsx(Path file, RowConsumer consumer) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headers = null;
            int rowNumber = 0;
            for (Row row : sheet) {
                if (headers == null) {
                    headers = new ArrayList<>();
                    for (Cell cell : row) {
                        headers.add(formatCellValue(cell));
                    }
                    continue;
                }
                rowNumber++;
                Map<String, Object> data = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    data.put(headers.get(i), formatCellValue(cell));
                }
                consumer.accept(rowNumber, data);
            }
        }
    }

    /**
     * {@code DataFormatter.formatCellValue(Cell)} calls {@code cell.getSheet().getWorkbook()}
     * (to resolve the 1904 date system) for *any* cell carrying a non-default number format —
     * not just date formats. {@link com.monitorjbl.xlsx.impl.StreamingSheet} doesn't support
     * {@code getWorkbook()} and always throws, so {@code DataFormatter} can't be used at all
     * against a {@link StreamingReader}-backed sheet; formatting is done here instead, reading
     * only the cell's own type/style/value.
     */
    private static String formatCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case BLANK, _NONE -> null;
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatNumericCellValue(cell);
            default -> cell.toString();
        };
    }

    private static String formatNumericCellValue(Cell cell) {
        double value = cell.getNumericCellValue();
        CellStyle style = cell.getCellStyle();
        if (DateUtil.isValidExcelDate(value) && DateUtil.isADateFormat(style.getDataFormat(), style.getDataFormatString())) {
            LocalDateTime dateTime = DateUtil.getLocalDateTime(value);
            return dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? dateTime.toLocalDate().toString()
                : dateTime.toString();
        }
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static void readCsv(Path file, RowConsumer consumer) throws IOException {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        ObjectReader reader = mapper.readerFor(ROW_TYPE).with(schema);
        int rowNumber = 0;
        try (MappingIterator<Map<String, Object>> rows = reader.readValues(file.toFile())) {
            while (rows.hasNext()) {
                rowNumber++;
                consumer.accept(rowNumber, rows.next());
            }
        }
    }

    private static void readJson(Path file, RowConsumer consumer) throws IOException {
        try (JsonParser parser = JSON_MAPPER.getFactory().createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Expected a top-level JSON array of row objects: " + file.getFileName());
            }
            int rowNumber = 0;
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                rowNumber++;
                Map<String, Object> data = JSON_MAPPER.readValue(parser, ROW_TYPE);
                consumer.accept(rowNumber, data);
            }
        }
    }
}
