package in.qualtechedge.qcp.templates.dto.response;

import java.util.List;

public record TransformationResponse(String field, List<Mapping> mappings) {

    public record Mapping(String from, String to) {
    }
}
