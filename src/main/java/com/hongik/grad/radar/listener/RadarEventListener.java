package com.hongik.grad.radar.listener;

import com.hongik.grad.analysis.event.ReportCreatedEvent;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.radar.application.RadarEmitterRegistry;
import com.hongik.grad.radar.application.RadarService;
import com.hongik.grad.radar.dto.RadarPoint;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes committed disclosures (gray point) and reports (same disclosure_id, now with expected_car)
 * to every connected radar. Mirrors the existing AFTER_COMMIT + {@code @Async} listeners; failures
 * are logged and never reach the publishing transaction.
 */
@Component
public class RadarEventListener {

  private static final Logger log = LoggerFactory.getLogger(RadarEventListener.class);

  private final RadarService radarService;
  private final RadarEmitterRegistry registry;

  public RadarEventListener(RadarService radarService, RadarEmitterRegistry registry) {
    this.radarService = radarService;
    this.registry = registry;
  }

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDisclosureCreated(DisclosureCreatedEvent event) {
    try {
      Set<UUID> users = registry.connectedUsers();
      if (!users.isEmpty()) {
        push(radarService.streamPoints(event.disclosureId(), "disclosure", users));
      }
    } catch (RuntimeException ex) {
      log.error(
          "Failed to push radar point for disclosure_id={}: {}",
          event.disclosureId(),
          ex.getMessage(),
          ex);
    }
  }

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReportCreated(ReportCreatedEvent event) {
    try {
      Set<UUID> users = registry.connectedUsers();
      if (!users.isEmpty()) {
        push(radarService.streamPointsForReport(event.reportId(), users));
      }
    } catch (RuntimeException ex) {
      log.error(
          "Failed to push radar update for report_id={}: {}",
          event.reportId(),
          ex.getMessage(),
          ex);
    }
  }

  private void push(Map<UUID, RadarPoint> points) {
    points.forEach((userId, point) -> registry.send(userId, point.event(), point));
  }
}
