package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.ApiConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.ApiConfigResponse;
import java.util.List;

public interface ApiConfigService {

    ApiConfigResponse create(ApiConfigRequest request);

    ApiConfigResponse getById(String configId);

    List<ApiConfigResponse> getAll();

    ApiConfigResponse update(String configId, ApiConfigRequest request);

    ApiConfigResponse submit(String configId);

    ApiConfigResponse accept(String configId);

    ApiConfigResponse reject(String configId, RejectRequest request);
}
