package in.qualtechedge.qcp.templates.service.keycloak.impl;

import in.qualtechedge.qcp.templates.dto.request.KeycloakTokenRequest;
import in.qualtechedge.qcp.templates.dto.response.KeycloakTokenResponse;
import in.qualtechedge.qcp.templates.exception.KeycloakAuthenticationException;
import in.qualtechedge.qcp.templates.properties.KeycloakProperties;
import in.qualtechedge.qcp.templates.service.keycloak.KeycloakTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakTokenServiceImpl implements KeycloakTokenService {

    private final RestTemplate restTemplate;
    private final KeycloakProperties keycloakProperties;

    @Override
    public KeycloakTokenResponse getToken(String tenant, KeycloakTokenRequest request) {
        log.info("Requesting token from Keycloak: tenant={}, user={}, grant_type={}, client_id={}",
                tenant, request.getUsername(), request.getGrantType(), request.getClientId());

        String tokenUrl = buildTokenUrl(tenant);
        log.info("Keycloak token URL: {}", tokenUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", request.getGrantType());
        body.add("client_id", request.getClientId());

        if (StringUtils.hasText(request.getClientSecret())) {
            body.add("client_secret", request.getClientSecret());
        }

        if (request.isPasswordGrant()) {
            body.add("username", request.getUsername());
            body.add("password", request.getPassword());
        }

        body.add("scope", StringUtils.hasText(request.getScope()) ? request.getScope() : "openid");

        log.info("Request body (excluding secrets): grant_type={}, client_id={}, username={}, scope={}",
                request.getGrantType(), request.getClientId(), request.getUsername(),
                StringUtils.hasText(request.getScope()) ? request.getScope() : "openid");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Sending token request to Keycloak: {}", tokenUrl);
            ResponseEntity<KeycloakTokenResponse> response =
                    restTemplate.postForEntity(tokenUrl, entity, KeycloakTokenResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                KeycloakTokenResponse tokenResponse = response.getBody();
                tokenResponse.setTenant(tenant);
                log.info("Token obtained successfully: tenant={}, user={}", tenant, request.getUsername());
                return tokenResponse;
            }

            throw new KeycloakAuthenticationException("Failed to obtain token from Keycloak");
        } catch (HttpClientErrorException e) {
            log.error("Keycloak authentication failed: tenant={}, user={}, status={}",
                    tenant, request.getUsername(), e.getStatusCode());
            log.error("Keycloak error response: {}", e.getResponseBodyAsString());
            log.error("Full error details: {}", e.getMessage());

            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new KeycloakAuthenticationException("Invalid credentials: " + e.getResponseBodyAsString());
            }
            throw new KeycloakAuthenticationException("Authentication failed: " + e.getMessage());
        } catch (KeycloakAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during Keycloak authentication: tenant={}", tenant, e);
            throw new KeycloakAuthenticationException("Authentication service unavailable: " + e.getMessage());
        }
    }

    @Override
    public KeycloakTokenResponse getClientCredentialsToken(String tenant, String clientId, String clientSecret) {
        log.info("Requesting client_credentials token: tenant={}, client={}", tenant, clientId);

        String tokenUrl = buildTokenUrl(tenant);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("scope", "openid");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<KeycloakTokenResponse> response =
                    restTemplate.postForEntity(tokenUrl, entity, KeycloakTokenResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                KeycloakTokenResponse tokenResponse = response.getBody();
                tokenResponse.setTenant(tenant);
                log.info("Client credentials token obtained: tenant={}, client={}", tenant, clientId);
                return tokenResponse;
            }

            throw new KeycloakAuthenticationException("Failed to obtain client credentials token");
        } catch (HttpClientErrorException e) {
            log.error("Client credentials auth failed: tenant={}, client={}, status={}",
                    tenant, clientId, e.getStatusCode());

            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new KeycloakAuthenticationException("Invalid client credentials");
            }
            throw new KeycloakAuthenticationException("Authentication failed: " + e.getMessage());
        } catch (KeycloakAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during client credentials authentication: tenant={}", tenant, e);
            throw new KeycloakAuthenticationException("Authentication service unavailable: " + e.getMessage());
        }
    }

    @Override
    public KeycloakTokenResponse refreshToken(String tenant, String clientId, String clientSecret,
                                              String refreshToken) {
        log.info("Refreshing token: tenant={}", tenant);

        String tokenUrl = buildTokenUrl(tenant);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        body.add("client_id", clientId);

        if (StringUtils.hasText(clientSecret)) {
            body.add("client_secret", clientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<KeycloakTokenResponse> response =
                    restTemplate.postForEntity(tokenUrl, entity, KeycloakTokenResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                KeycloakTokenResponse tokenResponse = response.getBody();
                tokenResponse.setTenant(tenant);
                log.info("Token refreshed successfully: tenant={}", tenant);
                return tokenResponse;
            }

            throw new KeycloakAuthenticationException("Failed to refresh token");
        } catch (HttpClientErrorException e) {
            log.error("Token refresh failed: tenant={}, status={}", tenant, e.getStatusCode());
            throw new KeycloakAuthenticationException("Token refresh failed: " + e.getMessage());
        } catch (KeycloakAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during token refresh: tenant={}", tenant, e);
            throw new KeycloakAuthenticationException("Token refresh service unavailable: " + e.getMessage());
        }
    }

    @Override
    public void logout(String tenant, String clientId, String clientSecret, String refreshToken) {
        log.info("Logging out: tenant={}", tenant);

        String logoutUrl = keycloakProperties.getServerUrl() + "/realms/" + tenant
                + "/protocol/openid-connect/logout";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("refresh_token", refreshToken);
        body.add("client_id", clientId);

        if (StringUtils.hasText(clientSecret)) {
            body.add("client_secret", clientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(logoutUrl, entity, Void.class);
            log.info("Logout successful: tenant={}", tenant);
        } catch (Exception e) {
            log.error("Error during logout: tenant={}", tenant, e);
        }
    }

    @Override
    public boolean introspectToken(String tenant, String clientId, String clientSecret, String token) {
        log.debug("Introspecting token: tenant={}", tenant);

        String introspectUrl = keycloakProperties.getServerUrl() + "/realms/" + tenant
                + "/protocol/openid-connect/token/introspect";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);
        body.add("client_id", clientId);

        if (StringUtils.hasText(clientSecret)) {
            body.add("client_secret", clientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(introspectUrl, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Boolean active = (Boolean) response.getBody().get("active");
                return Boolean.TRUE.equals(active);
            }
            return false;
        } catch (Exception e) {
            log.error("Error during token introspection: tenant={}", tenant, e);
            return false;
        }
    }

    @Override
    public KeycloakTokenResponse exchangeCodeForToken(String tokenUrl,
                                                      MultiValueMap<String, String> formData) {
        log.info("Exchanging authorization code for tokens at: {}", tokenUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<KeycloakTokenResponse> response =
                    restTemplate.postForEntity(tokenUrl, entity, KeycloakTokenResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Authorization code exchange successful");
                return response.getBody();
            }

            throw new KeycloakAuthenticationException("Failed to exchange authorization code for tokens");
        } catch (HttpClientErrorException e) {
            log.error("Token exchange failed: status={}, response={}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new KeycloakAuthenticationException(
                        "Invalid authorization code or client credentials: " + e.getResponseBodyAsString());
            }
            throw new KeycloakAuthenticationException("Token exchange failed: " + e.getMessage());
        } catch (KeycloakAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during token exchange", e);
            throw new KeycloakAuthenticationException("Token exchange service unavailable: " + e.getMessage());
        }
    }

    private String buildTokenUrl(String tenant) {
        return keycloakProperties.getServerUrl() + "/realms/" + tenant + "/protocol/openid-connect/token";
    }
}
