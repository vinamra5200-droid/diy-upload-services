package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.FieldType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Column-to-field mapping ({@code template_fields}) (admin-api-contract.md §2.2 fields[]). */
@Entity
@Table(name = "template_fields")
@Getter
@Setter
@NoArgsConstructor
public class TemplateField {

    @Id
    @Column(name = "field_id")
    private String fieldId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "source_column", nullable = false)
    private String sourceColumn;

    @Column(name = "target_field", nullable = false)
    private String targetField;

    @Column(name = "field_label", nullable = false)
    private String fieldLabel;

    /** Persisted via {@link in.qualtechedge.qcp.templates.enums.FieldTypeConverter} (autoApply) —
     * do NOT add {@code @Enumerated} here, it would take precedence over the converter and
     * write the Java constant name ("STRING") instead of the wire/DB value ("string"), which
     * the {@code template_fields_field_type_check} constraint rejects. */
    @Column(name = "field_type", nullable = false)
    private FieldType fieldType = FieldType.STRING;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
