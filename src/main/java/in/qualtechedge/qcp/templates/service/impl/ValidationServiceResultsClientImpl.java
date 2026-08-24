package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ValidationServiceRowsResponse;
import in.qualtechedge.qcp.templates.properties.ValidationServiceProperties;
import in.qualtechedge.qcp.templates.service.ValidationServiceResultsClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Pages through {@code GET /api/v1/batch-uploads/{batchId}/rows} on validation-service
 * (0-indexed pages, {@code one-indexed-parameters: false} per its api-standards) until a page
 * comes back short of a full page, accumulating every row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceResultsClientImpl implements ValidationServiceResultsClient {

    private final RestTemplate restTemplate;
    private final ValidationServiceProperties validationServiceProperties;

    @Override
    public List<ValidationServiceRowsResponse.Row> fetchAllRows(UUID batchId) {
        List<ValidationServiceRowsResponse.Row> allRows = new ArrayList<>();
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            String url = validationServiceProperties.getBaseUrl()
                    + "/api/v1/batch-uploads/{batchId}/rows?page={page}&size={size}";
            ValidationServiceRowsResponse response = restTemplate.getForObject(url,
                    ValidationServiceRowsResponse.class, batchId, page, validationServiceProperties.getResultsPageSize());
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
