package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.response.CheckerInboxItemResponse;
import in.qualtechedge.qcp.templates.entity.CheckerInboxItem;
import org.springframework.stereotype.Component;

@Component
public class CheckerInboxMapper {

    public CheckerInboxItemResponse toResponse(CheckerInboxItem entity) {
        return new CheckerInboxItemResponse(
                entity.getChangeId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getEntityLabel(),
                entity.getSummary(),
                entity.getSubmittedBy(),
                entity.getSubmittedAt(),
                entity.isActorNeSubmitter(),
                entity.getProcessIdRef());
    }
}
