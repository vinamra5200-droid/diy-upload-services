package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.request.StorageConfigRequest;
import in.qualtechedge.qcp.templates.dto.response.StorageConfigResponse;
import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.SecretMasking;
import org.springframework.stereotype.Component;

@Component
public class StorageConfigMapper {

    public StorageConfig toEntity(StorageConfigRequest request, String updatedBy) {
        StorageConfig entity = new StorageConfig();
        entity.setConfigId(IdGenerator.generate("stg"));
        applyRequest(entity, request);
        entity.setStatus(ConfigStatus.draft);
        entity.setUpdatedBy(updatedBy);
        return entity;
    }

    public void updateEntity(StorageConfig entity, StorageConfigRequest request, String updatedBy) {
        applyRequest(entity, request);
        entity.setUpdatedBy(updatedBy);
    }

    /**
     * A blank {@code secretAccessKey} means "leave the stored secret unchanged" — the UI only
     * ever shows a masked value (see {@link #toResponse}) and cannot resend the real one.
     */
    private void applyRequest(StorageConfig entity, StorageConfigRequest request) {
        entity.setProvider(request.provider());
        entity.setConnectionLabel(request.connectionLabel());
        entity.setConnectionRef(request.connectionRef());
        entity.setBucketName(request.bucketName());
        entity.setBucketRegion(request.bucketRegion());
        entity.setAccessKeyId(request.accessKeyId());
        if (request.secretAccessKey() != null && !request.secretAccessKey().isBlank()) {
            entity.setSecretAccessKey(request.secretAccessKey());
        }
        entity.setHostname(request.hostname());
        entity.setPort(request.port());
    }

    public StorageConfigResponse toResponse(StorageConfig entity) {
        return new StorageConfigResponse(
                entity.getConfigId(),
                entity.getProvider(),
                entity.getConnectionLabel(),
                entity.getConnectionRef(),
                entity.getBucketName(),
                entity.getBucketRegion(),
                entity.getAccessKeyId(),
                SecretMasking.mask(entity.getSecretAccessKey()),
                entity.getHostname(),
                entity.getPort(),
                entity.getPathPattern(),
                entity.getStatus(),
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt());
    }
}
