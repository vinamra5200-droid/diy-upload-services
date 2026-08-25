package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.properties.ValidationServiceProperties;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Pages through {@code GET /api/v1/internal/batch-uploads/{batchId}/rows} on validation-service
 * (0-indexed pages, {@code one-indexed-parameters: false} per its api-standards) until a page
 * comes back short of a full page, accumulating every row. The internal path — not the
 * maker-facing {@code /api/v1/batch-uploads/{batchId}/rows} — is excluded from both Keycloak
 * bearer auth and Host-subdomain tenant resolution on validation-service's side, the same trust
 * boundary its completion callback to this service already has; the tenant is carried in the
 * {@code X-Tenant-Code} header since there's no request body on a GET to carry it in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceResultsClientImpl implements ValidationServiceResultsClient {

    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private final RestTemplate restTemplate;
    private final ValidationServiceProperties validationServiceProperties;

    @Override
    public List<ValidationServiceRowsResponse.Row> fetchAllRows(UUID batchId, String tenantCode) {
        List<ValidationServiceRowsResponse.Row> allRows = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add(TENANT_CODE_HEADER, tenantCode);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            String url = validationServiceProperties.getBaseUrl()
                    + "/api/v1/internal/batch-uploads/{batchId}/rows?page={page}&size={size}";
            ValidationServiceRowsResponse response = restTemplate.exchange(url, HttpMethod.GET, entity,
                    ValidationServiceRowsResponse.class, batchId, page, validationServiceProperties.getResultsPageSize())
                    .getBody();
            if (response == null || response.data() == null) {
                log.warn("Empty rows response from validation-service: batchId={}, page={}", batchId, page);
                break;
            }
            allRows.addAll(response.data().content());
            totalPages = response.data().page().totalPages();
            page++;
        }
        log.debug("Fetched rows from validation-service: batchId={}, rowCount={}", batchId, allRows.size());
        return allRows;
    }
}
