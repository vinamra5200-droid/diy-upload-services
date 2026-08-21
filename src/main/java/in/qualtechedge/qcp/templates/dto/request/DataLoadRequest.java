package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.DuplicateRowAction;
import in.qualtechedge.qcp.templates.enums.RowOrderMode;
import in.qualtechedge.qcp.templates.enums.SortDirection;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DataLoadRequest(
        @NotNull(message = "dataLoad.primaryKeyFields must not be null")
        List<String> primaryKeyFields,

        @NotNull(message = "dataLoad.duplicateAction must not be null")
        DuplicateRowAction duplicateAction,

        @NotNull(message = "dataLoad.rowOrder must not be null")
        RowOrderMode rowOrder,

        @NotNull(message = "dataLoad.sortFields must not be null")
        List<SortFieldEntry> sortFields
) {

    public record SortFieldEntry(String field, SortDirection direction) {
    }
}
