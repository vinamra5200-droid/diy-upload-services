package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-template XLSX/CSV/JSON parse options ({@code template_upload_formats})
 * (admin-api-contract.md §2.2 uploadFormats). Auto-seeded (3 rows) by the
 * {@code templates_seed_formats} DB trigger on every {@code templates} INSERT.
 */
@Entity
@Table(name = "template_upload_formats")
@IdClass(TemplateUploadFormatId.class)
@Getter
@Setter
@NoArgsConstructor
public class TemplateUploadFormat {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "format_key")
    private UploadFormatKey formatKey;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "max_size_mb", nullable = false)
    private int maxSizeMb;

    @Column(name = "sheet_name")
    private String sheetName;

    private String delimiter;

    private String charset;

    @Column(name = "header_row")
    private Integer headerRow;

    @Column(name = "root_array_path")
    private String rootArrayPath;
}
