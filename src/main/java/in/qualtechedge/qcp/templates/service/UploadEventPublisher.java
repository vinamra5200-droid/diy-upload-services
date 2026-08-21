package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.UploadFileResponse;
import in.qualtechedge.qcp.templates.enums.UploadFileStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory SSE pub-sub for upload status changes, keyed by uploadId — lets the frontend watch
 * one upload finish (see {@link in.qualtechedge.qcp.templates.controller.MakerUploadController#events})
 * without polling {@code GET /api/v1/uploads/{uploadId}}.
 * <p>
 * Single-instance only: a multi-instance deployment would need this backed by something shared
 * (e.g. Redis pub/sub) so an emitter registered on one instance still hears about a status change
 * published by {@link in.qualtechedge.qcp.templates.service.impl.UploadS3Worker} on another.
 */
@Component
@Slf4j
public class UploadEventPublisher {

    private final Map<String, List<SseEmitter>> emittersByUploadId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String uploadId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUploadId.computeIfAbsent(uploadId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> unregister(uploadId, emitter));
        emitter.onTimeout(() -> unregister(uploadId, emitter));
        emitter.onError(e -> unregister(uploadId, emitter));
        return emitter;
    }

    /** Completes and drops every emitter for this upload once it reaches a terminal status. */
    public void publish(UploadFileResponse status) {
        List<SseEmitter> emitters = emittersByUploadId.get(status.uploadId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        boolean terminal = status.status() == UploadFileStatus.completed || status.status() == UploadFileStatus.failed;
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name("status").data(status));
                if (terminal) {
                    emitter.complete();
                }
            } catch (IOException e) {
                log.debug("SSE emitter for upload {} is gone, dropping it", status.uploadId());
                emitter.completeWithError(e);
            }
        }
    }

    private void unregister(String uploadId, SseEmitter emitter) {
        emittersByUploadId.computeIfPresent(uploadId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
