package com.hongik.grad.notifications.listener;

import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.notifications.application.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to a committed {@link DisclosureCreatedEvent} on a background thread and fans the
 * disclosure out to every user that favorited its ticker — independent of analysis. Coexists with
 * {@code analysis.listener.DisclosureCreatedListener} (report generation) on the same event;
 * failures are logged without affecting other listeners.
 */
@Component
public class DisclosureNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(DisclosureNotificationListener.class);

  private final NotificationService notificationService;

  public DisclosureNotificationListener(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDisclosureCreated(DisclosureCreatedEvent event) {
    try {
      notificationService.fanOutDisclosureCreated(
          event.disclosureId(), event.ticker(), event.title());
    } catch (RuntimeException ex) {
      log.error(
          "Failed to fan out disclosure notifications for disclosure_id={} ticker={}: {}",
          event.disclosureId(),
          event.ticker(),
          ex.getMessage(),
          ex);
    }
  }
}
