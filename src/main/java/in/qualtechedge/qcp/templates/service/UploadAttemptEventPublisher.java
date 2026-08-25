package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.UploadAttemptResponse;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory SSE pub-sub for upload-attempt status changes, keyed by attemptId — the emitter
 * registry behind {@code GET /api/v1/upload/attempts/{attemptId}/events}
 * (upload-api-contract.md §2.2/§2.2a), same shape as {@link UploadEventPublisher} for raw
 * uploads.
 * <p>
 * Two event names, decided purely from the attempt's status at publish time:
 * <ul>
 *   <li>{@code attempt} — the attempt is still {@code ACCEPTED}/{@code VALIDATING}; connection
 *       stays open.</li>
 *   <li>{@code done} — the attempt left {@code ACCEPTED}/{@code VALIDATING} for good (whichever
 *       status it landed on); every emitter for that attempt gets this event once, then this
 *       class completes and drops it.</li>
 * </ul>
 * A comment heartbeat ({@code :ping}) goes out to every open emitter periodically so an
 * intermediary proxy/load balancer doesn't kill an idle-looking connection while validation is
 * still running.
 * <p>
 * Single-instance only: a multi-instance deployment would need this backed by something shared
 * (e.g. Redis pub/sub) so an emitter registered on one instance still hears about a status change
 * published — from {@link in.qualtechedge.qcp.templates.service.impl.UploadAttemptServiceImpl},
 * {@link in.qualtechedge.qcp.templates.service.impl.BatchValidationResultServiceImpl}, or
 * {@link in.qualtechedge.qcp.templates.scheduler.UploadPipelineReaper} — on another. This
 * template's docker-compose.yml runs exactly one instance of this service, so that gap doesn't
 * apply yet; it becomes a hard requirement the day a second instance or a load balancer without
 * sticky sessions is introduced.
 */
@Component
@Slf4j
public class UploadAttemptEventPublisher {

    private static final String EVENT_ATTEMPT = "attempt";
    private static final String EVENT_DONE = "done";

    private final Map<String, List<SseEmitter>> emittersByAttemptId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String attemptId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByAttemptId.computeIfAbsent(attemptId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> unregister(attemptId, emitter));
        emitter.onTimeout(() -> unregister(attemptId, emitter));
        emitter.onError(e -> unregister(attemptId, emitter));
        return emitter;
    }

    /**
     * Sends {@code attempt} while the attempt is still in flight, or {@code done} (and closes
     * every subscribed emitter) once it has left {@code ACCEPTED}/{@code VALIDATING} — called
     * both for the subscriber's first event (current state, covering the race where the attempt
     * already finished before the client connected) and for every later state change.
     */
    public void publish(UploadAttemptResponse response) {
        List<SseEmitter> emitters = emittersByAttemptId.get(response.uploadAttemptId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        boolean inFlight = response.status() == UploadAttemptStatus.ACCEPTED
                || response.status() == UploadAttemptStatus.VALIDATING;
        String eventName = inFlight ? EVENT_ATTEMPT : EVENT_DONE;
        for (SseEmitter emitter : List.copyOf(emitters)) {
            // Unregistered here explicitly rather than left to the onCompletion/onError
            // callbacks subscribe() wires up: a terminal publish() can complete an emitter on
            // the same thread and before the controller method has even returned (the
            // subscribe()-then-immediately-terminal race in UploadAttemptServiceImpl#subscribe)
            // — at that point Spring MVC hasn't started async processing yet, and empirically the
            // completion callback does not fire promptly for that case, leaving a dead emitter in
            // this map that every heartbeat() tick then fails to send to, forever. Dropping it
            // here, synchronously, is correct in every case, not just that one.
            boolean drop = !inFlight;
            try {
                emitter.send(SseEmitter.event().name(eventName).data(response));
                if (!inFlight) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE emitter for upload attempt {} is gone, dropping it", response.uploadAttemptId());
                try {
                    emitter.completeWithError(e);
                } catch (IllegalStateException alreadyDone) {
                    // Already completed/errored by someone else — nothing left to signal.
                }
                drop = true;
            }
            if (drop) {
                unregister(response.uploadAttemptId(), emitter);
            }
        }
    }

    /** Keeps intermediary proxies/load balancers from treating a quiet in-flight validation as a
     * dead connection (upload-api-contract.md §2.2: "at least every 15s"). Also self-heals the
     * registry: an emitter that's gone stale for any reason fails its send here and gets dropped,
     * the same explicit-unregister belt {@link #publish} relies on. */
    @Scheduled(fixedRateString = "${qcp.upload.attempt-events-heartbeat-interval-ms:10000}")
    public void heartbeat() {
        for (Map.Entry<String, List<SseEmitter>> entry : emittersByAttemptId.entrySet()) {
            String attemptId = entry.getKey();
            for (SseEmitter emitter : List.copyOf(entry.getValue())) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    log.debug("SSE emitter heartbeat failed for upload attempt {}, dropping it", attemptId);
                    unregister(attemptId, emitter);
                }
            }
        }
    }

    private void unregister(String attemptId, SseEmitter emitter) {
        emittersByAttemptId.computeIfPresent(attemptId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
