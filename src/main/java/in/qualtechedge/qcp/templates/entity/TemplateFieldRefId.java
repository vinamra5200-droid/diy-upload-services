package in.qualtechedge.qcp.templates.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code @IdClass} shape (templateId, targetField) for {@link TemplateTransformation}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateFieldRefId implements Serializable {
    private String templateId;
    private String targetField;
}
