package com.hongik.grad.radar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RadarEmitterRegistryTest {

  private final RadarEmitterRegistry registry = new RadarEmitterRegistry(Duration.ofMinutes(30));
  private final UUID user = UUID.randomUUID();

  @Test
  void connect_registersUserWithConfiguredTimeout() {
    SseEmitter emitter = registry.connect(user);

    assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(30).toMillis());
    assertThat(registry.connectedUsers()).containsExactly(user);
    assertThat(registry.connectionCount(user)).isEqualTo(1);
  }

  @Test
  void send_toCompletedEmitter_removesItAndForgetsUser() {
    SseEmitter emitter = registry.connect(user);
    registry.send(user, "disclosure", "x"); // buffered before servlet init; must not drop it
    assertThat(registry.connectionCount(user)).isEqualTo(1);

    emitter.complete();
    registry.send(user, "disclosure", "y");

    assertThat(registry.connectionCount(user)).isZero();
    assertThat(registry.connectedUsers()).isEmpty();
  }

  @Test
  void heartbeat_dropsDeadEmittersOnly() {
    SseEmitter dead = registry.connect(user);
    registry.connect(user);
    dead.complete();

    registry.heartbeat();

    assertThat(registry.connectionCount(user)).isEqualTo(1);
  }
}
