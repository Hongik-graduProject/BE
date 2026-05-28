package com.hongik.grad.analysis.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportReadStatusRepository extends JpaRepository<ReportReadStatus, Long> {

  Optional<ReportReadStatus> findByUserIdAndReportId(UUID userId, Long reportId);

  List<ReportReadStatus> findByUserIdAndReportIdIn(UUID userId, Collection<Long> reportIds);

  boolean existsByUserIdAndReportId(UUID userId, Long reportId);
}
