package in.qualtechedge.qcp.templates.multitenancy.provisioning;

import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import in.qualtechedge.qcp.templates.multitenancy.registry.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On {@code ApplicationReady}, brings every ACTIVE registry tenant fully online:
 * database provisioned (if missing), Flyway migrated, HikariCP pool registered
 * (QCC Multi-Tenancy §4 — pools at startup + lazy registration afterwards).
 * <p>
 * A failing tenant is logged and skipped so one bad tenant never blocks the others
 * or the application; tenants registered after startup are picked up lazily by the
 * datasource manager or onboarded immediately via the admin API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantStartupInitializer {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTenants() {
        List<Tenant> activeTenants = tenantRepository.findAllByStatus(Tenant.STATUS_ACTIVE);
        log.info("Initializing {} active tenant(s) from the registry...", activeTenants.size());

        int ready = 0;
        for (Tenant tenant : activeTenants) {
            long start = System.currentTimeMillis();
            try {
                tenantProvisioningService.onboard(tenant);
                ready++;
                log.info("Tenant '{}' ready in {} ms", tenant.getShortCode(), System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("Tenant '{}' initialization failed — skipping: {}", tenant.getShortCode(), e.getMessage(), e);
            }
        }
        log.info("Tenant initialization completed: {}/{} tenant(s) ready", ready, activeTenants.size());
    }
}
