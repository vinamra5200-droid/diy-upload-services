package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;

public interface ProcessService {

    ProcessResponse create(ProcessRequest request);

    ProcessResponse getById(String processId);

    PageResponse<ProcessResponse> list(ConfigStatus status, String search, int page, int limit);

    ProcessResponse update(String processId, ProcessRequest request);

    ProcessResponse submit(String processId);

    ProcessResponse accept(String processId);

    ProcessResponse reject(String processId, RejectRequest request);
}
