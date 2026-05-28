package com.hongik.grad.analysis.routing;

import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * MVP router: picks the stock's rank=1 (Primary) theme from {@code stock_themes}. {@code score} =
 * {@code stock_themes.probability}. Returns empty if the ticker is outside the learning universe.
 */
@Component
public class PrimaryFixedRouter implements ThemeRouter {

  private final StockThemeRepository stockThemeRepository;

  public PrimaryFixedRouter(StockThemeRepository stockThemeRepository) {
    this.stockThemeRepository = stockThemeRepository;
  }

  @Override
  public Optional<RoutingResult> route(Disclosure disclosure) {
    String ticker = disclosure.getStock().getTicker();
    List<StockTheme> themes = stockThemeRepository.findByStockTickerOrderByRankAsc(ticker);
    if (themes.isEmpty()) {
      return Optional.empty();
    }
    StockTheme primary = themes.get(0);
    return Optional.of(
        new RoutingResult(
            primary.getTheme().getId(), primary.getTheme().getName(), primary.getProbability()));
  }
}
