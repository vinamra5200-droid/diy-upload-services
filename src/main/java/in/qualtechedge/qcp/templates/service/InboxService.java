package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.CheckerInboxItemResponse;
import java.util.List;

public interface InboxService {

    List<CheckerInboxItemResponse> list(String actorId);

    /**
     * Return type is deliberately {@link Object}: routes to the matching entity's accept endpoint
     * internally (admin-api-contract.md §8.2), and each entity type has its own response DTO
     * shape (ProcessResponse, TemplateResponse, ...).
     */
    Object accept(String changeId);

    Object reject(String changeId, RejectRequest request);
}
