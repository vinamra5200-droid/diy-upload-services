package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.StorageConfigRequest;
import in.qualtechedge.qcp.templates.dto.response.StorageConfigResponse;
import java.util.List;

public interface StorageConfigService {

    StorageConfigResponse create(StorageConfigRequest request);

    StorageConfigResponse getById(String configId);

    List<StorageConfigResponse> getAll();

    StorageConfigResponse update(String configId, StorageConfigRequest request);

    StorageConfigResponse submit(String configId);

    StorageConfigResponse accept(String configId);

    StorageConfigResponse reject(String configId, RejectRequest request);
}
