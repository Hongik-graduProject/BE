package com.hongik.grad.analysis.listener;

import com.hongik.grad.analysis.application.AnalysisReportGenerator;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to a freshly committed {@link DisclosureCreatedEvent}, runs the analysis pipeline on a
 * background thread, and persists a report in its own REQUIRES_NEW transaction via the generator.
 * Passes only disclosureId to avoid LazyInitializationException in the async context (M4).
 */
@Component
public class DisclosureCreatedListener {

  private static final Logger log = LoggerFactory.getLogger(DisclosureCreatedListener.class);

  private final AnalysisReportGenerator analysisReportGenerator;

  public DisclosureCreatedListener(AnalysisReportGenerator analysisReportGenerator) {
    this.analysisReportGenerator = analysisReportGenerator;
  }

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDisclosureCreated(DisclosureCreatedEvent event) {
    try {
      analysisReportGenerator.generate(event.disclosureId());
    } catch (RuntimeException ex) {
      log.error(
          "Failed to generate analysis_report for disclosure_id={} ticker={}: {}",
          event.disclosureId(),
          event.ticker(),
          ex.getMessage(),
          ex);
    }
  }
}
