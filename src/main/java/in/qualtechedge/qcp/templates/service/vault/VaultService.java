package in.qualtechedge.qcp.templates.service.vault;

import java.util.Map;

public interface VaultService {
    /**
     * Retrieves tenant secret from HashiCorp Vault.
     *
     * @param tenantCode The tenant code to retrieve secrets for
     * @return Map containing the tenant secret data
     * @throws RuntimeException if retrieval fails or vault is disabled
     */
    Map<String, Object> getTenantSecret(String tenantCode);

    /**
     * Creates or updates tenant secret in HashiCorp Vault.
     *
     * @param tenantCode The tenant code to create/update secrets for
     * @param secrets    The secrets data to store
     * @return Map containing the response metadata
     * @throws RuntimeException if operation fails or vault is disabled
     */
    Map<String, Object> createOrUpdateTenantSecret(String tenantCode, Map<String, Object> secrets);

    /**
     * Updates specific fields in tenant secret in HashiCorp Vault.
     * This performs a partial update by merging with existing data.
     *
     * @param tenantCode The tenant code to update secrets for
     * @param secrets    The secrets data to update/merge
     * @return Map containing the response metadata
     * @throws RuntimeException if operation fails or vault is disabled
     */
    Map<String, Object> updateTenantSecret(String tenantCode, Map<String, Object> secrets);

    /**
     * Deletes tenant secret from HashiCorp Vault.
     *
     * @param tenantCode The tenant code to delete secrets for
     * @throws RuntimeException if operation fails or vault is disabled
     */
    void deleteTenantSecret(String tenantCode);
}
