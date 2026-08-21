package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TransformationRequest(
        @NotBlank(message = "transformations[].field must not be blank")
        String field,

        @NotNull(message = "transformations[].mappings must not be null")
        List<Mapping> mappings
) {

    public record Mapping(String from, String to) {
    }
}
