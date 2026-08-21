package in.qualtechedge.qcp.templates.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backs {@link in.qualtechedge.qcp.templates.service.impl.ValidationServiceResultsClientImpl} —
 * the base URL validation-service's REST API is reachable at, so this repo can pull row-wise
 * failed-row results once a batch completes. Not under {@code spring.*}, matching
 * {@link KafkaBatchProperties}'s convention.
 */
@Component
@ConfigurationProperties(prefix = "qcp.validation-service")
@Data
public class ValidationServiceProperties {

    /** e.g. http://validation-service:9942 (container network) or http://localhost:9943 (host). */
    private String baseUrl;

    /** Page size used when paging through the failed-rows endpoint. */
    private int resultsPageSize = 200;
}
