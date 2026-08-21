package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateUploadFormatId implements Serializable {
    private String templateId;
    private UploadFormatKey formatKey;
}
