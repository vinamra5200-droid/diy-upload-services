package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.service.PostLoadActionDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs {@link PostLoadActionDispatcher#dispatch} off the request thread that handled
 * {@code POST /api/v1/upload/jobs/{jobId}/dispatch}, so that endpoint can return as soon as
 * {@link in.qualtechedge.qcp.templates.service.impl.UploadJobServiceImpl#dispatch} has flipped the
 * job to {@code PROCESSING}, instead of holding the HTTP request open for the S3 read + Kafka
 * publish. Must be a separate Spring bean from {@code UploadJobServiceImpl} — {@code @Async} is
 * proxy-based, so calling an {@code @Async} method on {@code this} from within the same class
 * silently runs synchronously instead (same constraint {@link UploadS3Worker} documents).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostLoadActionDispatchWorker {

    private final PostLoadActionDispatcher postLoadActionDispatcher;

    @Async("uploadTaskExecutor")
    public void run(String tenant, UploadJob job, Template template) {
        // The @Async proxy hands this off to a fresh thread pool thread, which doesn't inherit the
        // request thread's HostContext ThreadLocal — without this, tenant-routed repository calls
        // inside dispatch() below silently fall back to the system DB instead of the tenant's own.
        HostContext.setCurrentTenant(tenant);
        try {
            postLoadActionDispatcher.dispatch(job, template);
        } finally {
            HostContext.clear();
        }
    }
}
