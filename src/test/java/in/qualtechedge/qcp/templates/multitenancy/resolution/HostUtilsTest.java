package in.qualtechedge.qcp.templates.multitenancy.resolution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * Subdomain extraction per the QCP convention {tenant}-{product}-{env}.qualtechedge.in
 * (QCC Multi-Tenancy §2.1).
 */
class HostUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "client1-app-dev.qualtechedge.in, client1",
            "client1-app-dev.qualtechedge.in:8080, client1",
            "qc-custconnect-prod.qualtechedge.in, qc",
            "client2-app-local.qualtechedge.in, client2",
    })
    @DisplayName("tenant subdomain resolves to the first '-' segment")
    void extractsTenantFromSubdomain(String host, String expectedTenant) {
        assertThat(HostUtils.extractSubdomain(host)).isEqualTo(expectedTenant);
    }

    @Test
    @DisplayName("admin-* subdomain is the superadmin console — no tenant")
    void adminSubdomainHasNoTenant() {
        assertThat(HostUtils.extractSubdomain("admin-app-dev.qualtechedge.in")).isNull();
    }

    @Test
    @DisplayName("localhost and bare domains carry no tenant")
    void bareHostsHaveNoTenant() {
        assertThat(HostUtils.extractSubdomain("localhost")).isNull();
        assertThat(HostUtils.extractSubdomain("localhost:8080")).isNull();
        assertThat(HostUtils.extractSubdomain("qualtechedge.in")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/empty host headers are handled safely")
    void nullAndEmptyAreSafe(String host) {
        assertThat(HostUtils.extractSubdomain(host)).isNull();
        assertThat(HostUtils.extractDomain(host)).isNull();
    }

    @Test
    @DisplayName("extractDomain strips protocol, port and path")
    void extractDomainStripsPortAndPath() {
        assertThat(HostUtils.extractDomain("client1-app-dev.qualtechedge.in:8080"))
                .isEqualTo("client1-app-dev.qualtechedge.in");
    }
}
