package in.qualtechedge.qcp.templates.scheduler;

import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import in.qualtechedge.qcp.templates.multitenancy.registry.TenantRepository;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Force-releases any {@code processes.config_locked} held longer than
 * {@code qcp.upload.stale-lock-timeout-minutes} — the safety valve for a lock nothing ever cleared
 * (validation-service crashed mid-batch, its completion event never arrived, or the Kafka publish
 * step itself hung before reaching either success or failure). A lock with no recovery path would
 * otherwise block that process's templates forever.
 * <p>
 * {@code processes} is a per-tenant table, and this runs with no request/tenant context (it's a
 * scheduled job, not a request), so it visits every active tenant explicitly — same async-boundary
 * rule {@link HostContext}'s own doc comment calls out for {@code @Async}/executors/messaging.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigLockReaper {

    private final TenantRepository tenantRepository;
    private final ConfigLockService configLockService;

    @Value("${qcp.upload.stale-lock-timeout-minutes}")
    private int staleLockTimeoutMinutes;

    @Scheduled(fixedDelayString = "${qcp.upload.stale-lock-reaper-interval-ms:300000}")
    public void releaseStaleLocks() {
        for (Tenant tenant : tenantRepository.findAllByStatus(Tenant.STATUS_ACTIVE)) {
            HostContext.setCurrentTenant(tenant.getShortCode());
            try {
                int released = configLockService.releaseStale(staleLockTimeoutMinutes);
                if (released > 0) {
                    log.warn("Stale config lock reaper released {} lock(s) held longer than {} minute(s): tenant={}",
                            released, staleLockTimeoutMinutes, tenant.getShortCode());
                }
            } finally {
                HostContext.clear();
            }
        }
    }
}
