package in.qualtechedge.qcp.templates.dto.request;

import java.util.List;

/**
 * One Kafka message published by {@link in.qualtechedge.qcp.templates.service.impl.PostLoadActionDispatcherImpl}
 * to the topic a template's {@code post_load_action} (kafkaTopic) names, once a maker/UI-triggered
 * dispatch call streams that job's completed file off S3. A distinct type from {@link BatchChunkMessage}
 * even though the shape rhymes — different topic, different (future, not-yet-built) consumer, no
 * validation-rules payload. {@code jobId} is the send key, so every chunk of one job lands on one
 * partition, ordered; {@code lastChunk} on the final chunk (including an empty one) tells the
 * consumer the job is fully streamed.
 */
public record PostLoadActionChunkMessage(
        String jobId,
        String tenantCode,
        String processCode,
        String templateCode,
        String templateVersion,
        Integer chunkSequence,
        Boolean lastChunk,
        Integer totalRecords,
        List<RowPayload> rows
) {
}
