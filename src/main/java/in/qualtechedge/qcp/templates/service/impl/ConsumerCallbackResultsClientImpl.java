package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ConsumerCallbackBatchesResponse;
import in.qualtechedge.qcp.templates.properties.ConsumerCallbackServiceProperties;
import in.qualtechedge.qcp.templates.service.ConsumerCallbackResultsClient;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Same {@code X-Tenant-Code} + internal-path pattern as {@link ValidationServiceResultsClientImpl}
 * — the internal endpoint on consumer-callback-service's side is excluded from both Keycloak
 * bearer auth and Host-subdomain tenant resolution, reachable only on the private network.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumerCallbackResultsClientImpl implements ConsumerCallbackResultsClient {

    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private final RestTemplate restTemplate;
    private final ConsumerCallbackServiceProperties consumerCallbackServiceProperties;

    @Override
    public ConsumerCallbackBatchesResponse.Data fetchBatchesPage(String jobId, String tenantCode, String outcome,
            int page, int size) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TENANT_CODE_HEADER, tenantCode);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(consumerCallbackServiceProperties.getBaseUrl()
                        + "/api/v1/internal/callback-jobs/{jobId}/batches")
                .queryParam("page", page)
                .queryParam("size", size);
        if (outcome != null && !outcome.isBlank()) {
            uriBuilder.queryParam("outcome", outcome);
        }

        ConsumerCallbackBatchesResponse response = restTemplate
                .exchange(uriBuilder.build(jobId), HttpMethod.GET, entity, ConsumerCallbackBatchesResponse.class)
                .getBody();
        if (response == null || response.data() == null) {
            log.warn("Empty batches response from consumer-callback-service: jobId={}, page={}", jobId, page);
            return new ConsumerCallbackBatchesResponse.Data(List.of(),
                    new ConsumerCallbackBatchesResponse.PageMeta(page, size, 0L, 0));
        }
        return response.data();
    }

    @Override
    public void streamBatches(String jobId, String tenantCode, Consumer<List<ConsumerCallbackBatchesResponse.Batch>> pageHandler) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TENANT_CODE_HEADER, tenantCode);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        int page = 0;
        int totalPages = 1;
        long batchCount = 0;
        while (page < totalPages) {
            String url = consumerCallbackServiceProperties.getBaseUrl()
                    + "/api/v1/internal/callback-jobs/{jobId}/batches?page={page}&size={size}";
            ConsumerCallbackBatchesResponse response = restTemplate.exchange(url, HttpMethod.GET, entity,
                    ConsumerCallbackBatchesResponse.class, jobId, page, consumerCallbackServiceProperties.getBatchesPageSize())
                    .getBody();
            if (response == null || response.data() == null) {
                log.warn("Empty batches response from consumer-callback-service: jobId={}, page={}", jobId, page);
                break;
            }
            List<ConsumerCallbackBatchesResponse.Batch> content = response.data().content();
            pageHandler.accept(content);
            batchCount += content.size();
            totalPages = response.data().page().totalPages();
            page++;
        }
        log.debug("Streamed batches from consumer-callback-service: jobId={}, batchCount={}", jobId, batchCount);
    }
}
