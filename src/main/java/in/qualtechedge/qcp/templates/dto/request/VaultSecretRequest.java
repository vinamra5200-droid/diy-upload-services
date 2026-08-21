package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class VaultSecretRequest {
    
    @NotNull(message = "Secrets data is required")
    @NotEmpty(message = "Secrets data cannot be empty")
    private Map<String, Object> secrets;
}
