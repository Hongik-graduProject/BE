package com.hongik.grad.themes.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockThemeRepository extends JpaRepository<StockTheme, Long> {

  List<StockTheme> findByStockTickerOrderByRankAsc(String ticker);

  Optional<StockTheme> findByStockTickerAndThemeIdAndModelVersion(
      String ticker, Long themeId, String modelVersion);
}
