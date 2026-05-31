package com.hongik.grad.notifications.listener;

import com.hongik.grad.analysis.event.ReportCreatedEvent;
import com.hongik.grad.notifications.application.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to a committed {@link ReportCreatedEvent} on a background thread and fans the report out
 * to every user that favorited its ticker. Mirrors the {@code DisclosureCreatedListener} pattern
 * (AFTER_COMMIT + {@code @Async}); failures are logged without affecting the report transaction.
 */
@Component
public class ReportCreatedListener {

  private static final Logger log = LoggerFactory.getLogger(ReportCreatedListener.class);

  private final NotificationService notificationService;

  public ReportCreatedListener(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReportCreated(ReportCreatedEvent event) {
    try {
      notificationService.fanOutReportCreated(event.reportId(), event.ticker(), event.title());
    } catch (RuntimeException ex) {
      log.error(
          "Failed to fan out notifications for report_id={} ticker={}: {}",
          event.reportId(),
          event.ticker(),
          ex.getMessage(),
          ex);
    }
  }
}
