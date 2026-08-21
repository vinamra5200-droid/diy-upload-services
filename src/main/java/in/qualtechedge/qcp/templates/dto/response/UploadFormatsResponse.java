package in.qualtechedge.qcp.templates.dto.response;

public record UploadFormatsResponse(Entry xlsx, Entry csv, Entry json) {

    public record Entry(
            boolean enabled,
            int maxSizeMb,
            String sheetName,
            String delimiter,
            String charset,
            Integer headerRow,
            String rootArrayPath
    ) {
    }
}
