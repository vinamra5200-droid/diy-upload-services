package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Upload-level maker-checker role refs ({@code template_checker_roles}) (admin-api-contract.md §2.4 makerChecker.checkerRoles). */
@Entity
@Table(name = "template_checker_roles")
@IdClass(TemplateCheckerRoleId.class)
@Getter
@Setter
@NoArgsConstructor
public class TemplateCheckerRole {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Id
    @Column(name = "role_ref")
    private String roleRef;
}
