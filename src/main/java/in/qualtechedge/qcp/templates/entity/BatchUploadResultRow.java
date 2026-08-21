package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One failed row of a {@link BatchUploadResult} ({@code batch_upload_result_rows}), mirroring
 * validation-service's own {@code batch_upload_row} shape. {@code rowData}/{@code errors} are
 * JSONB columns kept as raw JSON text, converted in the mapper layer (same convention as
 * {@link TemplateValidationRule}).
 */
@Entity
@Table(name = "batch_upload_result_rows")
@Getter
@Setter
@NoArgsConstructor
public class BatchUploadResultRow {

    @Id
    private String id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_data", nullable = false, columnDefinition = "jsonb")
    private String rowData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String errors;
}
