package in.qualtechedge.qcp.templates.dto.request;

import java.util.List;

/**
 * One {@code template_transformations} row, embedded on the Kafka wire. Sent on every chunk of a
 * batch ({@link BatchChunkMessage#transformations()}), same as {@link ValidationRuleMessage} — see
 * {@link BatchChunkMessage}'s javadoc for why the snapshot can't be pinned to {@code chunkSequence
 * == 0}. Field-for-field mirror of validation-service's own {@code TransformationMessage}, and of
 * this repo's own {@code TransformationResponse} (same shape).
 */
public record TransformationMessage(
        String field,
        List<Mapping> mappings
) {

    public record Mapping(String from, String to) {
    }
}
