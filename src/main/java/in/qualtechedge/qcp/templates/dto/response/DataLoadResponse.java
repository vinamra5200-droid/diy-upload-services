package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.DuplicateRowAction;
import in.qualtechedge.qcp.templates.enums.RowOrderMode;
import in.qualtechedge.qcp.templates.enums.SortDirection;
import java.util.List;

public record DataLoadResponse(
        List<String> primaryKeyFields,
        DuplicateRowAction duplicateAction,
        RowOrderMode rowOrder,
        List<SortFieldEntry> sortFields
) {

    public record SortFieldEntry(String field, SortDirection direction) {
    }
}
