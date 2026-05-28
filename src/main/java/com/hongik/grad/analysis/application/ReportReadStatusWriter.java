package com.hongik.grad.analysis.application;

import com.hongik.grad.analysis.domain.ReportReadStatus;
import com.hongik.grad.analysis.domain.ReportReadStatusRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated writer for ReportReadStatus so the REQUIRES_NEW transaction runs through a real Spring
 * AOP proxy when invoked from a readOnly query method in another bean.
 */
@Service
public class ReportReadStatusWriter {

  private static final Logger log = LoggerFactory.getLogger(ReportReadStatusWriter.class);

  private final ReportReadStatusRepository reportReadStatusRepository;

  public ReportReadStatusWriter(ReportReadStatusRepository reportReadStatusRepository) {
    this.reportReadStatusRepository = reportReadStatusRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markRead(UUID userId, Long reportId) {
    if (reportReadStatusRepository.existsByUserIdAndReportId(userId, reportId)) {
      return;
    }
    try {
      reportReadStatusRepository.save(new ReportReadStatus(userId, reportId));
    } catch (DataIntegrityViolationException ignored) {
      log.debug(
          "Concurrent read-status insert race for userId={} reportId={} — already recorded",
          userId,
          reportId);
    }
  }
}
