package in.qualtechedge.qcp.templates.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backs {@link in.qualtechedge.qcp.templates.service.impl.UploadS3Worker} — the
 * {@code POST /api/v1/uploads/{processId}/{templateId}} endpoint returns as soon as the file is
 * staged and checksummed, and the actual S3 PUT runs on this pool so the HTTP request doesn't
 * stay open for the whole upload. The frontend follows progress over SSE, not by polling.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "uploadTaskExecutor")
    public Executor uploadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("upload-worker-");
        executor.initialize();
        return executor;
    }
}
