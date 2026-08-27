package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.DatabaseActionMode;
import in.qualtechedge.qcp.templates.enums.DatabaseProvider;
import in.qualtechedge.qcp.templates.enums.KafkaMode;
import in.qualtechedge.qcp.templates.enums.PostLoadActionType;
import jakarta.validation.constraints.NotNull;

/**
 * {@code kafkaMode}/{@code kafkaQueueConfigId} mirror {@code databaseMode}/{@code databaseConnectionId}
 * below: {@code useExisting} binds a saved {@code QueueConfig} (Queue Orchestration) instead of
 * typing {@code kafkaTopic}/{@code kafkaBootstrapServers} by hand; {@code custom} or {@code null}
 * keeps those two direct fields in play. "Required when kafkaMode = useExisting" is enforced by
 * the frontend Zod validator, not here — same pattern as every other conditional field on this
 * record.
 */
public record PostLoadActionRequest(
        @NotNull(message = "postLoadAction.type must not be null")
        PostLoadActionType type,

        String kafkaTopic,
        String kafkaBootstrapServers,
        KafkaMode kafkaMode,
        String kafkaQueueConfigId,
        DatabaseActionMode databaseMode,
        String databaseConnectionId,
        String databaseTableName,
        DatabaseProvider databaseProvider,
        String databaseConnectionRef
) {
}
