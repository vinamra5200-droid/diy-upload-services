package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Field value transformations ({@code template_transformations}) (admin-api-contract.md §2.2
 * transformations[]). {@code mappings} is a JSONB array kept as raw JSON text, converted in
 * {@link in.qualtechedge.qcp.templates.mapper.TemplateMapper}.
 */
@Entity
@Table(name = "template_transformations")
@IdClass(TemplateFieldRefId.class)
@Getter
@Setter
@NoArgsConstructor
public class TemplateTransformation {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Id
    @Column(name = "target_field")
    private String targetField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String mappings = "[]";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
