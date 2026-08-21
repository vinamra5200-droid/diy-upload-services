package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.DatabaseActionMode;
import in.qualtechedge.qcp.templates.enums.DatabaseProvider;
import in.qualtechedge.qcp.templates.enums.PostLoadActionType;
import jakarta.validation.constraints.NotNull;

public record PostLoadActionRequest(
        @NotNull(message = "postLoadAction.type must not be null")
        PostLoadActionType type,

        String kafkaTopic,
        String kafkaBootstrapServers,
        DatabaseActionMode databaseMode,
        String databaseConnectionId,
        String databaseTableName,
        DatabaseProvider databaseProvider,
        String databaseConnectionRef
) {
}
