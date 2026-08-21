package in.qualtechedge.qcp.templates.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Provides the {@link RestTemplate} bean used by the Keycloak token service to call
 * Keycloak's token endpoint. When {@code http-client.ssl-verify=false} (local/dev), all
 * SSL certificates are trusted so a self-signed Keycloak certificate does not block calls.
 */
@Configuration
@Slf4j
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${http-client.ssl-verify:false}") boolean sslVerify) {
        log.info("Creating RestTemplate bean with SSL verify: {}", sslVerify);

        if (!sslVerify) {
            log.warn("SSL verification disabled for RestTemplate (ssl-verify=false) — do NOT use in production");
            return buildTrustAllRestTemplate();
        }

        return new RestTemplate();
    }

    private RestTemplate buildTrustAllRestTemplate() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            return new RestTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build trust-all RestTemplate", e);
        }
    }
}
