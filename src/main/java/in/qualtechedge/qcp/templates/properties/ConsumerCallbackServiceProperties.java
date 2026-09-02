package in.qualtechedge.qcp.templates.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backs {@link in.qualtechedge.qcp.templates.service.impl.ConsumerCallbackResultsClientImpl} — the
 * base URL consumer-callback-service's internal REST API is reachable at, so this repo can pull
 * per-batch delivery detail for a job's callback drill-down. Not under {@code spring.*}, matching
 * {@link ValidationServiceProperties}'s convention.
 */
@Component
@ConfigurationProperties(prefix = "qcp.consumer-callback-service")
@Data
public class ConsumerCallbackServiceProperties {

    /** e.g. http://consumer-callback-service:9942 (container network) or http://localhost:9944 (host). */
    private String baseUrl;

    /** Default page size when a caller doesn't specify one. */
    private int batchesPageSize = 50;
}
