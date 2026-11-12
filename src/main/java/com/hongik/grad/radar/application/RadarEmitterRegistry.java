package com.hongik.grad.radar.application;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open radar SSE connections per user.
 *
 * <p>shortcut: in-memory emitter registry, single instance only; move to Redis pub/sub when scaling
 * out.
 */
@Component
public class RadarEmitterRegistry {

  private static final Logger log = LoggerFactory.getLogger(RadarEmitterRegistry.class);

  private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final Duration timeout;

  public RadarEmitterRegistry(@Value("${radar.sse-timeout:PT30M}") Duration timeout) {
    this.timeout = timeout;
  }

  public SseEmitter connect(UUID userId) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());
    emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> remove(userId, emitter));
    // complete() on timeout ends the stream normally instead of an AsyncRequestTimeoutException
    emitter.onTimeout(emitter::complete);
    emitter.onError(ex -> remove(userId, emitter));
    return emitter;
  }

  public Set<UUID> connectedUsers() {
    return Set.copyOf(emitters.keySet());
  }

  public void send(UUID userId, String eventName, Object data) {
    for (SseEmitter emitter : emitters.getOrDefault(userId, Set.of())) {
      trySend(userId, emitter, SseEmitter.event().name(eventName).data(data));
    }
  }

  // Servlet gives no disconnect signal; a periodic write is what detects dead clients.
  @Scheduled(fixedRate = 20_000)
  public void heartbeat() {
    emitters.forEach(
        (userId, set) ->
            set.forEach(emitter -> trySend(userId, emitter, SseEmitter.event().comment("ping"))));
  }

  int connectionCount(UUID userId) {
    return emitters.getOrDefault(userId, Set.of()).size();
  }

  private void trySend(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException | IllegalStateException ex) {
      // client gone or emitter already completed; the container finishes the async request itself
      log.debug("Dropping radar emitter user={}: {}", userId, ex.getMessage());
      remove(userId, emitter);
    }
  }

  private void remove(UUID userId, SseEmitter emitter) {
    emitters.computeIfPresent(
        userId,
        (k, set) -> {
          set.remove(emitter);
          return set.isEmpty() ? null : set;
        });
  }
}
