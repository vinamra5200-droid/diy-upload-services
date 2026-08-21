package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.SortDirection;
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

/** Row sort order used when {@code dataLoad.rowOrder = "sortByKey"} ({@code template_sort_fields}). */
@Entity
@Table(name = "template_sort_fields")
@IdClass(TemplateFieldRefId.class)
@Getter
@Setter
@NoArgsConstructor
public class TemplateSortField {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Id
    @Column(name = "target_field")
    private String targetField;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SortDirection direction = SortDirection.asc;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
