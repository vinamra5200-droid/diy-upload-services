package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.QueueConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.QueueConfigResponse;
import java.util.List;

public interface QueueConfigService {

    QueueConfigResponse create(QueueConfigRequest request);

    QueueConfigResponse getById(String configId);

    List<QueueConfigResponse> getAll();

    QueueConfigResponse update(String configId, QueueConfigRequest request);

    QueueConfigResponse submit(String configId);

    /** Also creates {@code topic} on the shared broker (partitions/replicationFactor) — see impl javadoc. */
    QueueConfigResponse accept(String configId);

    QueueConfigResponse reject(String configId, RejectRequest request);
}
