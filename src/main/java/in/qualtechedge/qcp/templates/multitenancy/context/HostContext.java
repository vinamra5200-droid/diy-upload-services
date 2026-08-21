package in.qualtechedge.qcp.templates.multitenancy.context;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Holds the current tenant and host for the active request thread (QCC Multi-Tenancy §3).
 * <p>
 * The {@code TenantResolutionFilter} populates this context at the start of every request and
 * clears it in a {@code finally} block — no leakage across pooled threads. The tenant is mirrored
 * into the logging MDC ({@code tenant}, {@code host}) so every log line is attributable, and the
 * per-tenant SiftingAppender routes lines to a separate file per tenant.
 * <p>
 * Async boundary rule: when work hops threads ({@code @Async}, executors, messaging), copy the
 * tenant explicitly to the worker thread and clear it when done.
 */
@Slf4j
public final class HostContext {

    /** Tenant identifier used when no tenant is resolved — routes to the system (superadmin) DB. */
    public static final String SYSTEM_TENANT = "system";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_HOST = new ThreadLocal<>();

    private HostContext() {
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(String tenantCode) {
        log.debug("Setting tenant context to: {}", tenantCode);
        if (tenantCode == null) {
            CURRENT_TENANT.remove();
            MDC.remove("tenant");
        } else {
            CURRENT_TENANT.set(tenantCode);
            MDC.put("tenant", tenantCode);
        }
    }

    public static String getCurrentHost() {
        return CURRENT_HOST.get();
    }

    public static void setCurrentHost(String host) {
        if (host == null) {
            CURRENT_HOST.remove();
            MDC.remove("host");
        } else {
            CURRENT_HOST.set(host);
            MDC.put("host", host);
        }
    }

    /** Clears tenant and host from both the ThreadLocals and the MDC. Call in a finally block. */
    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_HOST.remove();
        MDC.remove("tenant");
        MDC.remove("host");
    }
}
