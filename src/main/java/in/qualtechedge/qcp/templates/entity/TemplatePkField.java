package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ordered data-load primary/composite key fields ({@code template_pk_fields}) (admin-api-contract.md §2.2 dataLoad.primaryKeyFields). */
@Entity
@Table(name = "template_pk_fields")
@IdClass(TemplateFieldRefId.class)
@Getter
@Setter
@NoArgsConstructor
public class TemplatePkField {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Id
    @Column(name = "target_field")
    private String targetField;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
