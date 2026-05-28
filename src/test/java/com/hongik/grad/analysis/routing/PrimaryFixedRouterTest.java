package com.hongik.grad.analysis.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrimaryFixedRouterTest {

  @Mock private StockThemeRepository stockThemeRepository;

  private PrimaryFixedRouter router;

  @BeforeEach
  void setUp() {
    router = new PrimaryFixedRouter(stockThemeRepository);
  }

  @Test
  @DisplayName("rank=1 테마를 routing 결과로 반환한다 (score = probability)")
  void route_returnsPrimaryTheme() throws Exception {
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    Disclosure disclosure =
        new Disclosure(
            stock, "20240101000001", "사업보고서", DisclosureType.REPORT, OffsetDateTime.now());

    Theme theme = createTheme(1L, "AI/반도체");
    StockTheme primary = createStockTheme(stock, theme, (short) 1, 0.9234);

    when(stockThemeRepository.findByStockTickerOrderByRankAsc("005930"))
        .thenReturn(List.of(primary));

    Optional<RoutingResult> result = router.route(disclosure);

    assertThat(result).isPresent();
    assertThat(result.get().themeId()).isEqualTo(1L);
    assertThat(result.get().themeName()).isEqualTo("AI/반도체");
    assertThat(result.get().score()).isEqualTo(0.9234);
  }

  @Test
  @DisplayName("학습 universe 외 종목은 empty 반환")
  void route_returnsEmptyForUnknownTicker() {
    Stock stock = new Stock("999999", "Unknown", Market.KOSPI);
    Disclosure disclosure =
        new Disclosure(
            stock, "20240101000002", "사업보고서", DisclosureType.REPORT, OffsetDateTime.now());

    when(stockThemeRepository.findByStockTickerOrderByRankAsc("999999")).thenReturn(List.of());

    assertThat(router.route(disclosure)).isEmpty();
  }

  private Theme createTheme(Long id, String name) throws Exception {
    Theme t = new Theme(name);
    setField(t, "id", id);
    return t;
  }

  private StockTheme createStockTheme(Stock stock, Theme theme, short rank, double probability)
      throws Exception {
    StockTheme st = new StockTheme(stock, theme, rank);
    setField(st, "probability", probability);
    return st;
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }
}
