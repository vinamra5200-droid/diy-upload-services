package in.qualtechedge.qcp.templates.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Custom JWT decoder that supports per-realm decoder creation and optional SSL bypass
 * for development environments where Keycloak uses a self-signed certificate.
 * <p>
 * The default {@link #decode(String)} method is not used — callers must obtain a
 * realm-specific decoder via {@link #createDecoderForIssuer(String, boolean)}.
 */
@Component
@Slf4j
public class CustomJwtDecoder implements JwtDecoder {

    public CustomJwtDecoder(@Value("${http-client.ssl-verify:false}") boolean sslVerify) {
        log.info("Initializing CustomJwtDecoder with SSL verify: {}", sslVerify);
    }

    /**
     * Create a realm-specific JWT decoder.
     *
     * @param issuer    the Keycloak realm issuer URL (e.g. {@code http://localhost:8080/realms/admin})
     * @param sslVerify whether to enforce SSL certificate verification
     * @return a fully configured {@link JwtDecoder} for the given issuer
     */
    public JwtDecoder createDecoderForIssuer(String issuer, boolean sslVerify) {
        log.info("Creating JWT decoder for issuer: {} with SSL verify: {}", issuer, sslVerify);

        String jwkSetUri = issuer + "/protocol/openid-connect/certs";
        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri);

        if (!sslVerify) {
            log.warn("SSL verification disabled for JWK Set URI fetch (ssl-verify=false) — do NOT use in production");
            builder.restOperations(buildTrustAllRestTemplate());
        }

        return builder.build();
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
            throw new IllegalStateException("Failed to build trust-all RestTemplate for JWK Set", e);
        }
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        throw new UnsupportedOperationException("Use createDecoderForIssuer() to obtain a realm-specific decoder");
    }
}
