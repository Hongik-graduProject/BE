package com.hongik.grad.analysis.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

  Page<AnalysisReport> findByStockTicker(String ticker, Pageable pageable);

  Optional<AnalysisReport> findByStockTickerAndDisclosureId(String ticker, Long disclosureId);
}
