package in.qualtechedge.qcp.templates.service.vault.impl;

import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.exception.VaultException;
import in.qualtechedge.qcp.templates.properties.VaultProperties;
import in.qualtechedge.qcp.templates.service.vault.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultServiceImpl implements VaultService {
    private final VaultProperties vaultProperties;
    private final RestTemplate restTemplate;

    @Value("${spring.profiles.active}")
    private String environment;

    private String getVaultEnvironment() {
        return "local".equalsIgnoreCase(environment) ? "dev" : environment;
    }

    /**
     * Performs AppRole login to HashiCorp Vault and returns the client token.
     * This is a private method called internally to authenticate with Vault.
     *
     * @return The Vault client token for authenticated requests
     * @throws RuntimeException if login fails or vault is disabled
     */
    private String loginWithAppRole() {
        if (!vaultProperties.isEnabled()) {
            log.error("Vault is disabled. Cannot perform AppRole login.");
            throw new IllegalStateException("Vault is disabled. Enable vault in application configuration.");
        }

        if (!"APPROLE".equalsIgnoreCase(vaultProperties.getAuthentication())) {
            log.error("Authentication method is not APPROLE. Current method: {}", vaultProperties.getAuthentication());
            throw new IllegalStateException(
                    "AppRole authentication is not configured. Current method: " + vaultProperties.getAuthentication());
        }

        try {
            String roleId = vaultProperties.getTenantAppRole().getRoleId();
            String secretId = vaultProperties.getTenantAppRole().getSecretId();
            String mountPath = vaultProperties.getMountPath();
            String vaultUrl = vaultProperties.getUrl();

            log.info("Attempting AppRole login to Vault at: {}", vaultUrl);
            log.debug("Using mount path: {}", mountPath);

            String loginUrl = String.format("%s/v1/auth/%s/login", vaultUrl, "approle");

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("role_id", roleId);
            requestBody.put("secret_id", secretId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    loginUrl,
                    HttpMethod.POST,
                    request,
                    Map.class);

            if (response.getBody() == null || !response.getBody().containsKey("auth")) {
                log.error("Invalid response from Vault: missing auth section");
                throw new IllegalStateException("Invalid Vault login response: missing auth section");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> auth = (Map<String, Object>) response.getBody().get("auth");
            String clientToken = (String) auth.get("client_token");

            if (clientToken == null || clientToken.isEmpty()) {
                log.error("No client token received from Vault");
                throw new IllegalStateException("No client token in Vault response. Check AppRole credentials.");
            }

            log.info("Successfully authenticated with Vault using AppRole");
            log.debug("Token lease duration: {} seconds", auth.get("lease_duration"));

            return clientToken;

        } catch (IllegalStateException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Vault login failed with client error: {} - {}", e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new VaultException("Vault authentication failed: Invalid AppRole credentials", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new VaultException("Vault access forbidden: Insufficient permissions for AppRole", e);
            } else {
                throw new VaultException("Vault login failed: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            log.error("Vault server error during login: {} - {}", e.getStatusCode(), e.getMessage());
            throw new VaultException("Vault server error during login: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("Vault connection failed: {}", e.getMessage());
            throw new VaultException("Cannot connect to Vault: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to login to Vault using AppRole: {}", e.getMessage(), e);
            throw new VaultException("Vault AppRole login failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves tenant secret from HashiCorp Vault.
     * Automatically authenticates using AppRole before retrieving secrets.
     *
     * @param tenantCode The tenant code to retrieve secrets for
     * @return Map containing the tenant secret data
     * @throws RuntimeException if retrieval fails or vault is disabled
     */
    @Override
    public Map<String, Object> getTenantSecret(String tenantCode) {
        if (!vaultProperties.isEnabled()) {
            log.error("Vault is disabled. Cannot retrieve tenant secret.");
            throw new IllegalStateException("Vault is disabled. Enable vault in application configuration.");
        }

        if (tenantCode == null || tenantCode.isEmpty()) {
            log.error("Tenant code is required");
            throw new IllegalArgumentException("Tenant code cannot be null or empty");
        }

        log.info("Authenticating with Vault using AppRole before retrieving tenant secret");
        String vaultToken = loginWithAppRole();

        try {
            String mountPath = vaultProperties.getMountPath();
            String applicationName = vaultProperties.getApplicationName();
            String vaultUrl = vaultProperties.getUrl();

            log.info("Retrieving tenant secret for tenant: {} from Vault", tenantCode);
            log.debug("Using mount path: {}, application: {}, environment: {}", mountPath, applicationName,
                    getVaultEnvironment());

            String secretUrl = String.format("%s/v1/%s/data/%s/%s/tenants/%s",
                    vaultUrl, mountPath, applicationName, getVaultEnvironment(), tenantCode);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", vaultToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    secretUrl,
                    HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getBody() == null) {
                log.error("Empty response from Vault for tenant: {}", tenantCode);
                throw new IllegalStateException("Empty response from Vault for tenant: " + tenantCode);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            if (!responseBody.containsKey("data")) {
                log.error("Invalid response from Vault: missing data section for tenant: {}", tenantCode);
                throw new IllegalStateException(
                        "Invalid Vault response: missing data section for tenant: " + tenantCode);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");

            if (!data.containsKey("data")) {
                log.error("Secret not found for tenant: {}", tenantCode);
                throw new ResourceNotFoundException("Tenant Secret not found for tenantCode: " + tenantCode);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> tenantData = (Map<String, Object>) data.get("data");

            log.info("Successfully retrieved tenant secret for tenant: {}", tenantCode);
            log.debug("Tenant secret metadata - version: {}", data.get("metadata"));

            return tenantData;

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Vault secret retrieval failed with client error for tenant {}: {} - {}", tenantCode,
                    e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Tenant Secret not found for tenantCode: " + tenantCode);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new VaultException("Access denied to tenant secret for: " + tenantCode, e);
            } else {
                throw new VaultException("Failed to retrieve tenant secret: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            log.error("Vault server error during secret retrieval for tenant {}: {} - {}", tenantCode,
                    e.getStatusCode(), e.getMessage());
            throw new VaultException("Vault server error during secret retrieval: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("Vault connection failed during secret retrieval: {}", e.getMessage());
            throw new VaultException("Cannot connect to Vault: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to retrieve tenant secret for tenant: {} - {}", tenantCode, e.getMessage(), e);
            throw new VaultException(
                    "Failed to retrieve tenant secret for tenant: " + tenantCode + ". " + e.getMessage(), e);
        }
    }

    /**
     * Creates or updates tenant secret in HashiCorp Vault.
     * Automatically authenticates using AppRole before creating/updating secrets.
     *
     * @param tenantCode The tenant code to create/update secrets for
     * @param secrets    The secrets data to store
     * @return Map containing the response metadata
     * @throws RuntimeException if operation fails or vault is disabled
     */
    @Override
    public Map<String, Object> createOrUpdateTenantSecret(String tenantCode, Map<String, Object> secrets) {
        if (!vaultProperties.isEnabled()) {
            log.error("Vault is disabled. Cannot create/update tenant secret.");
            throw new IllegalStateException("Vault is disabled. Enable vault in application configuration.");
        }

        if (tenantCode == null || tenantCode.isEmpty()) {
            log.error("Tenant code is required");
            throw new IllegalArgumentException("Tenant code cannot be null or empty");
        }

        if (secrets == null || secrets.isEmpty()) {
            log.error("Secrets data is required");
            throw new IllegalArgumentException("Secrets data cannot be null or empty");
        }

        log.info("Authenticating with Vault using AppRole before creating/updating tenant secret");
        String vaultToken = loginWithAppRole();

        try {
            String mountPath = vaultProperties.getMountPath();
            String applicationName = vaultProperties.getApplicationName();
            String vaultUrl = vaultProperties.getUrl();

            log.info("Creating/Updating tenant secret for tenant: {} in Vault", tenantCode);
            log.debug("Using mount path: {}, application: {}, environment: {}", mountPath, applicationName,
                    getVaultEnvironment());

            String secretUrl = String.format("%s/v1/%s/data/%s/%s/tenants/%s",
                    vaultUrl, mountPath, applicationName, getVaultEnvironment(), tenantCode);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("data", secrets);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", vaultToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    secretUrl,
                    HttpMethod.POST,
                    request,
                    Map.class);

            if (response.getBody() == null) {
                log.error("Empty response from Vault for tenant: {}", tenantCode);
                throw new IllegalStateException(
                        "Empty response from Vault while creating/updating secret for tenant: " + tenantCode);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            log.info("Successfully created/updated tenant secret for tenant: {}", tenantCode);
            log.debug("Response metadata: {}", responseBody.get("data"));

            return responseBody;

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Vault secret creation failed with client error for tenant {}: {} - {}", tenantCode,
                    e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new VaultException("Access denied to create tenant secret for: " + tenantCode, e);
            } else {
                throw new VaultException("Failed to create tenant secret: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            log.error("Vault server error during secret creation for tenant {}: {} - {}", tenantCode, e.getStatusCode(),
                    e.getMessage());
            throw new VaultException("Vault server error during secret creation: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("Vault connection failed during secret creation: {}", e.getMessage());
            throw new VaultException("Cannot connect to Vault: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to create/update tenant secret for tenant: {} - {}", tenantCode, e.getMessage(), e);
            throw new VaultException(
                    "Failed to create/update tenant secret for tenant: " + tenantCode + ". " + e.getMessage(), e);
        }
    }

    /**
     * Updates specific fields in tenant secret in HashiCorp Vault.
     * Automatically authenticates using AppRole before updating secrets.
     * This performs a partial update by merging with existing data.
     *
     * @param tenantCode The tenant code to update secrets for
     * @param secrets    The secrets data to update/merge
     * @return Map containing the response metadata
     * @throws RuntimeException if operation fails or vault is disabled
     */
    @Override
    public Map<String, Object> updateTenantSecret(String tenantCode, Map<String, Object> secrets) {
        if (!vaultProperties.isEnabled()) {
            log.error("Vault is disabled. Cannot update tenant secret.");
            throw new IllegalStateException("Vault is disabled. Enable vault in application configuration.");
        }

        if (tenantCode == null || tenantCode.isEmpty()) {
            log.error("Tenant code is required");
            throw new IllegalArgumentException("Tenant code cannot be null or empty");
        }

        if (secrets == null || secrets.isEmpty()) {
            log.error("Secrets data is required");
            throw new IllegalArgumentException("Secrets data cannot be null or empty");
        }

        log.info("Authenticating with Vault using AppRole before updating tenant secret");
        String vaultToken = loginWithAppRole();

        try {
            String mountPath = vaultProperties.getMountPath();
            String applicationName = vaultProperties.getApplicationName();
            String vaultUrl = vaultProperties.getUrl();

            log.info("Updating tenant secret for tenant: {} in Vault", tenantCode);
            log.debug("Using mount path: {}, application: {}, environment: {}", mountPath, applicationName,
                    getVaultEnvironment());

            // First, get existing secrets
            Map<String, Object> existingSecrets = getTenantSecret(tenantCode);

            // Merge with new secrets
            existingSecrets.putAll(secrets);

            String secretUrl = String.format("%s/v1/%s/data/%s/%s/tenants/%s",
                    vaultUrl, mountPath, applicationName, getVaultEnvironment(), tenantCode);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("data", existingSecrets);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", vaultToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    secretUrl,
                    HttpMethod.POST,
                    request,
                    Map.class);

            if (response.getBody() == null) {
                log.error("Empty response from Vault for tenant: {}", tenantCode);
                throw new IllegalStateException(
                        "Empty response from Vault while updating secret for tenant: " + tenantCode);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            log.info("Successfully updated tenant secret for tenant: {}", tenantCode);
            log.debug("Response metadata: {}", responseBody.get("data"));

            return responseBody;

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Vault secret update failed with client error for tenant {}: {} - {}", tenantCode,
                    e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Tenant Secret not found for tenantCode: " + tenantCode);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new VaultException("Access denied to update tenant secret for: " + tenantCode, e);
            } else {
                throw new VaultException("Failed to update tenant secret: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            log.error("Vault server error during secret update for tenant {}: {} - {}", tenantCode, e.getStatusCode(),
                    e.getMessage());
            throw new VaultException("Vault server error during secret update: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("Vault connection failed during secret update: {}", e.getMessage());
            throw new VaultException("Cannot connect to Vault: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to update tenant secret for tenant: {} - {}", tenantCode, e.getMessage(), e);
            throw new VaultException("Failed to update tenant secret for tenant: " + tenantCode + ". " + e.getMessage(),
                    e);
        }
    }

    /**
     * Deletes tenant secret from HashiCorp Vault.
     * Automatically authenticates using AppRole before deleting secrets.
     *
     * @param tenantCode The tenant code to delete secrets for
     * @throws RuntimeException if operation fails or vault is disabled
     */
    @Override
    public void deleteTenantSecret(String tenantCode) {
        if (!vaultProperties.isEnabled()) {
            log.error("Vault is disabled. Cannot delete tenant secret.");
            throw new IllegalStateException("Vault is disabled. Enable vault in application configuration.");
        }

        if (tenantCode == null || tenantCode.isEmpty()) {
            log.error("Tenant code is required");
            throw new IllegalArgumentException("Tenant code cannot be null or empty");
        }

        log.info("Authenticating with Vault using AppRole before deleting tenant secret");
        String vaultToken = loginWithAppRole();

        try {
            String mountPath = vaultProperties.getMountPath();
            String applicationName = vaultProperties.getApplicationName();
            String vaultUrl = vaultProperties.getUrl();

            log.info("Deleting tenant secret for tenant: {} from Vault", tenantCode);
            log.debug("Using mount path: {}, application: {}, environment: {}", mountPath, applicationName,
                    getVaultEnvironment());

            String secretUrl = String.format("%s/v1/%s/metadata/%s/%s/tenants/%s",
                    vaultUrl, mountPath, applicationName, getVaultEnvironment(), tenantCode);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", vaultToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            restTemplate.exchange(
                    secretUrl,
                    HttpMethod.DELETE,
                    request,
                    Void.class);

            log.info("Successfully deleted tenant secret for tenant: {}", tenantCode);

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Vault secret deletion failed with client error for tenant {}: {} - {}", tenantCode,
                    e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Tenant Secret not found for tenantCode: " + tenantCode);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new VaultException("Access denied to delete tenant secret for: " + tenantCode, e);
            } else {
                throw new VaultException("Failed to delete tenant secret: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            log.error("Vault server error during secret deletion for tenant {}: {} - {}", tenantCode, e.getStatusCode(),
                    e.getMessage());
            throw new VaultException("Vault server error during secret deletion: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("Vault connection failed during secret deletion: {}", e.getMessage());
            throw new VaultException("Cannot connect to Vault: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to delete tenant secret for tenant: {} - {}", tenantCode, e.getMessage(), e);
            throw new VaultException("Failed to delete tenant secret for tenant: " + tenantCode + ". " + e.getMessage(),
                    e);
        }
    }
}
