package com.hongik.grad.analysis.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

  Page<AnalysisReport> findByStockTicker(String ticker, Pageable pageable);

  Optional<AnalysisReport> findByStockTickerAndDisclosureId(String ticker, Long disclosureId);

  List<AnalysisReport> findByDisclosureIdIn(Collection<Long> disclosureIds);

  /**
   * Batch fetch for §3-6 enrichment; latest-per-ticker and unread counts derived in memory. {@code
   * appliedTheme} is fetched eagerly to avoid per-report Theme lookups in buildSummary.
   */
  @EntityGraph(attributePaths = {"appliedTheme"})
  List<AnalysisReport> findByStockTickerInOrderByGeneratedAtDesc(Collection<String> tickers);

  /**
   * Critique (반박 모드) loads the report in one short tx and then does network I/O with no tx open, so
   * stock/disclosure/appliedTheme must be fetched eagerly here — accessing them later on the
   * detached entity would otherwise throw LazyInitializationException.
   */
  @EntityGraph(attributePaths = {"stock", "disclosure", "appliedTheme"})
  Optional<AnalysisReport> findWithAssociationsById(Long id);
}
