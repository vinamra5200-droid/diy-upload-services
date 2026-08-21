package in.qualtechedge.qcp.templates.security.tenant;

import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the Keycloak realm resolved from the JWT {@code iss} claim for the active
 * request thread. Populated by {@link in.qualtechedge.qcp.templates.config.TenantAuthenticationManagerResolver}
 * after token validation and cleared at the end of every request.
 * <p>
 * Note: datasource routing uses {@link in.qualtechedge.qcp.templates.multitenancy.context.HostContext}
 * which is populated from the {@code Host} header subdomain. This context tracks the Keycloak
 * realm specifically, which is resolved from the JWT and may differ in casing from the subdomain.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_REALM = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenant(String tenant) {
        log.debug("Setting tenant context: {}", tenant);
        CURRENT_TENANT.set(tenant);
    }

    public static String getTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setRealm(String realm) {
        log.debug("Setting realm context: {}", realm);
        CURRENT_REALM.set(realm);
        CURRENT_SCHEMA.set(realm + "_schema");
    }

    public static String getRealm() {
        return CURRENT_REALM.get();
    }

    public static String getSchema() {
        return CURRENT_SCHEMA.get();
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
        CURRENT_REALM.remove();
        CURRENT_SCHEMA.remove();
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }

    public static boolean hasRealm() {
        return CURRENT_REALM.get() != null;
    }
}
