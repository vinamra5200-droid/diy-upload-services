package in.qualtechedge.qcp.templates.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCheckerRoleId implements Serializable {
    private String templateId;
    private String roleRef;
}
