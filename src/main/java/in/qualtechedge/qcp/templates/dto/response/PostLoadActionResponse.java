package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.DatabaseActionMode;
import in.qualtechedge.qcp.templates.enums.DatabaseProvider;
import in.qualtechedge.qcp.templates.enums.PostLoadActionType;

public record PostLoadActionResponse(
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
