package com.hongik.grad.themes.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /** 테마 탐색 — 한 테마를 주어진 순위로 가진 종목들. 정렬은 호출자의 {@link Pageable}. */
  @EntityGraph(attributePaths = {"stock"})
  Page<StockTheme> findByThemeIdAndRank(Long themeId, short rank, Pageable pageable);

  /**
   * 테마 탐색 목록: 1순위 종목을 시가총액 내림차순으로. Sort.nullsLast() 는 SQL 에 반영되지 않아 Postgres 가 NULL 을 맨 앞에 두므로
   * NULLS LAST 를 직접 쓴다.
   */
  @EntityGraph(attributePaths = {"stock"})
  @Query(
      value =
          "SELECT st FROM StockTheme st WHERE st.theme.id = :themeId AND st.rank = :rank "
              + "ORDER BY st.stock.marketCap DESC NULLS LAST, st.stock.ticker ASC",
      countQuery =
          "SELECT COUNT(st) FROM StockTheme st WHERE st.theme.id = :themeId AND st.rank = :rank")
  Page<StockTheme> findByThemeOrderByMarketCap(
      @Param("themeId") Long themeId, @Param("rank") short rank, Pageable pageable);
}
