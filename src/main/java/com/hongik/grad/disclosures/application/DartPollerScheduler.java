package com.hongik.grad.disclosures.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the periodic {@link DartPoller#poll()} trigger. Split from {@link DartPoller} so the
 * polling logic stays available as a bean (and directly invokable in tests) while the schedule
 * itself can be toggled off via {@code dart.poller.enabled=false}, keeping background polls out of
 * integration tests.
 */
@Component
@ConditionalOnProperty(name = "dart.poller.enabled", havingValue = "true", matchIfMissing = true)
public class DartPollerScheduler {

  private final DartPoller dartPoller;

  public DartPollerScheduler(DartPoller dartPoller) {
    this.dartPoller = dartPoller;
  }

  @Scheduled(fixedDelay = 60_000)
  public void trigger() {
    dartPoller.poll();
  }
}
