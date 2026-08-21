package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotNull;

public record UploadFormatsRequest(
        @NotNull(message = "uploadFormats.xlsx must not be null")
        Entry xlsx,

        @NotNull(message = "uploadFormats.csv must not be null")
        Entry csv,

        @NotNull(message = "uploadFormats.json must not be null")
        Entry json
) {

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
