package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.entity.ConfigLock;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.repository.ConfigLockRepository;
import in.qualtechedge.qcp.templates.repository.UploadProcessRepository;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigLockServiceImpl implements ConfigLockService {

    private final ConfigLockRepository configLockRepository;
    private final UploadProcessRepository uploadProcessRepository;

    @Override
    @Transactional
    public void acquire(String processId, String lockRef) {
        if (!uploadProcessRepository.existsById(processId)) {
            throw new ResourceNotFoundException("Process not found with id: " + processId);
        }
        ConfigLock lock = new ConfigLock();
        lock.setLockRef(lockRef);
        lock.setProcessId(processId);
        configLockRepository.save(lock);
        log.debug("Config lock acquired: processId={}, lockRef={}", processId, lockRef);
    }

    @Override
    @Transactional
    public void reassignRef(String processId, String oldRef, String newRef) {
        configLockRepository.findById(oldRef)
                .filter(lock -> lock.getProcessId().equals(processId))
                .ifPresentOrElse(lock -> {
                    configLockRepository.delete(lock);
                    ConfigLock reassigned = new ConfigLock();
                    reassigned.setLockRef(newRef);
                    reassigned.setProcessId(processId);
                    configLockRepository.save(reassigned);
                    log.debug("Config lock ref reassigned: processId={}, oldRef={}, newRef={}",
                            processId, oldRef, newRef);
                }, () -> log.warn("Skipped config lock ref reassignment — no lock held under ref: processId={}, oldRef={}",
                        processId, oldRef));
    }

    @Override
    @Transactional
    public void release(String lockRef) {
        configLockRepository.findById(lockRef).ifPresent(lock -> {
            configLockRepository.delete(lock);
            log.debug("Config lock released: processId={}, lockRef={}", lock.getProcessId(), lockRef);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLocked(String processId) {
        return configLockRepository.existsByProcessId(processId);
    }

    @Override
    @Transactional
    public int releaseStale(int timeoutMinutes) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(timeoutMinutes);
        List<ConfigLock> stale = configLockRepository.findByLockedAtBefore(cutoff);
        for (ConfigLock lock : stale) {
            log.warn("Force-releasing stale config lock: processId={}, lockRef={}, lockedAt={}",
                    lock.getProcessId(), lock.getLockRef(), lock.getLockedAt());
        }
        configLockRepository.deleteAll(stale);
        return stale.size();
    }
}
