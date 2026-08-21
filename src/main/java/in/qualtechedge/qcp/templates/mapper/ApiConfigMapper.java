package in.qualtechedge.qcp.templates.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import in.qualtechedge.qcp.templates.dto.request.ApiConfigRequest;
import in.qualtechedge.qcp.templates.dto.request.KeyValueRequest;
import in.qualtechedge.qcp.templates.dto.response.ApiAuthResponse;
import in.qualtechedge.qcp.templates.dto.response.ApiConfigResponse;
import in.qualtechedge.qcp.templates.dto.response.KeyValueResponse;
import in.qualtechedge.qcp.templates.entity.ApiConfig;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiConfigMapper {

    public ApiConfig toEntity(ApiConfigRequest request, String updatedBy) {
        ApiConfig entity = new ApiConfig();
        entity.setConfigId(IdGenerator.generate("apiconfig"));
        applyRequest(entity, request);
        entity.setStatus(ConfigStatus.draft);
        entity.setUpdatedBy(updatedBy);
        return entity;
    }

    public void updateEntity(ApiConfig entity, ApiConfigRequest request, String updatedBy) {
        applyRequest(entity, request);
        entity.setUpdatedBy(updatedBy);
    }

    private void applyRequest(ApiConfig entity, ApiConfigRequest request) {
        entity.setLabel(request.label());
        entity.setMethod(request.method());
        entity.setUri(request.uri());
        entity.setQueryParams(JsonColumnMapper.write(cleanPairs(request.queryParams())));
        entity.setHeaders(JsonColumnMapper.write(cleanPairs(request.headers())));
        entity.setBody(request.body() == null ? "" : request.body());
        entity.setAuth(JsonColumnMapper.write(request.auth()));
    }

    private List<KeyValueRequest> cleanPairs(List<KeyValueRequest> pairs) {
        if (pairs == null) {
            return List.of();
        }
        return pairs.stream().filter(pair -> pair.key() != null && !pair.key().isBlank()).toList();
    }

    public ApiConfigResponse toResponse(ApiConfig entity) {
        List<KeyValueResponse> queryParams = JsonColumnMapper.read(entity.getQueryParams(), new TypeReference<List<KeyValueResponse>>() {
        });
        List<KeyValueResponse> headers = JsonColumnMapper.read(entity.getHeaders(), new TypeReference<List<KeyValueResponse>>() {
        });
        ApiAuthResponse auth = JsonColumnMapper.read(entity.getAuth(), ApiAuthResponse.class);
        return new ApiConfigResponse(
                entity.getConfigId(),
                entity.getLabel(),
                entity.getMethod(),
                entity.getUri(),
                queryParams == null ? List.of() : queryParams,
                headers == null ? List.of() : headers,
                entity.getBody(),
                auth,
                entity.getStatus(),
                entity.getSubmittedBy(),
                entity.getRejectionReason(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt());
    }
}
