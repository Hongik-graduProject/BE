package com.hongik.grad.themes.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockThemeRepository extends JpaRepository<StockTheme, Long> {

  List<StockTheme> findByStockTickerOrderByRankAsc(String ticker);

  Optional<StockTheme> findByStockTickerAndThemeIdAndModelVersion(
      String ticker, Long themeId, String modelVersion);

  /**
   * Batch primary-theme lookup for §3-6 has_theme resolution. {@code theme} is fetched eagerly to
   * avoid per-row Theme lookups in buildSummary.
   */
  @EntityGraph(attributePaths = {"theme"})
  List<StockTheme> findByStockTickerInAndIsPrimaryTrue(Collection<String> tickers);
}
