package in.qualtechedge.qcp.templates.dto.request;

import java.util.List;
import java.util.UUID;

/**
 * One Kafka message published by {@link in.qualtechedge.qcp.templates.service.impl.BatchChunkPublisherImpl}
 * to validation-service's {@code data-validation-requested-topic} — field-for-field mirror of
 * validation-service's own {@code BatchChunkMessage}, which is what actually deserializes this
 * JSON on the other end ({@code BatchChunkListener}). {@code batchId} is {@link UUID}, not the
 * usual prefixed id convention ({@link in.qualtechedge.qcp.templates.utils.IdGenerator}) —
 * validation-service's field is typed {@code UUID}, so {@code upload_files.job_id} is minted as
 * a real UUID string precisely so it round-trips here. {@code rules} and {@code transformations}
 * carry the template's active validation rules and configured field transformations, sent on
 * every chunk of the batch (not just {@code chunkSequence == 0}) — chunks of one batch are spread
 * across all of the topic's partitions
 * ({@link in.qualtechedge.qcp.templates.service.impl.BatchChunkPublisherImpl}), so there's no
 * guarantee chunk 0 is consumed before any other chunk; a consumer that only cached rules/
 * transformations seen on chunk 0 could process an earlier-arriving later chunk before it ever had
 * them. {@code failFast} is the template's own row-evaluation setting and is sent alongside
 * {@code rules} for the same reason (repeated on every chunk, not just chunk 0).
 */
public record BatchChunkMessage(
        UUID batchId,
        String tenantCode,
        String processCode,
        String templateCode,
        String makerUserId,
        String fileName,
        Integer chunkSequence,
        Boolean lastChunk,
        List<RowPayload> rows,
        List<ValidationRuleMessage> rules,
        List<TransformationMessage> transformations,
        Boolean failFast
) {
}
