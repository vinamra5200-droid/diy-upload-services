package in.qualtechedge.qcp.templates.dto.response;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record TemplateResponse(
        String templateId,
        String templateCode,
        String templateName,
        String templateDescription,
        String version,
        String processId,
        ConfigStatus status,
        List<TemplateFieldResponse> fields,
        UploadFormatsResponse uploadFormats,
        PackageGateResponse packageGate,
        DataLoadResponse dataLoad,
        PostLoadActionResponse postLoadAction,
        int uploadProcessTimeoutMinutes,
        int validationWorkerThreads,
        MakerCheckerResponse makerChecker,
        List<TransformationResponse> transformations,
        boolean validationsEnabled,
        List<ValidationRuleResponse> rules,
        boolean failFast,
        ScheduleResponse schedule,
        String submittedBy,
        String rejectionReason,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
