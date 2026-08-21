package in.qualtechedge.qcp.templates.dto.request;

import in.qualtechedge.qcp.templates.enums.UploadFormatKey;

public record ScheduleRequest(
        boolean enabled,
        String frequency,
        String timeOfDay,
        String dayOfWeek,
        Integer dayOfMonth,
        String cronExpression,
        Pickup pickup,
        String filePattern,
        UploadFormatKey uploadFormat,
        boolean autoApprove,
        String lastRunAt,
        String nextRunAt
) {

    public record Pickup(String host, Integer port, String username, String credentialRef, String basePath) {
    }
}
