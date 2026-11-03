package com.hongik.grad.themes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.stocks.application.KrxQuoteService;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.dto.KrxQuote;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeRepository;
import com.hongik.grad.themes.dto.ThemeStocksResponse;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ThemeExploreServiceTest {

  @Mock private ThemeRepository themeRepository;
  @Mock private StockThemeRepository stockThemeRepository;
  @Mock private KrxQuoteService krxQuoteService;

  private ThemeExploreService service;

  @BeforeEach
  void setUp() {
    service = new ThemeExploreService(themeRepository, stockThemeRepository, krxQuoteService);
  }

  @Test
  void stocks_한_쪽의_종목과_시세를_합친다() throws Exception {
    Theme theme = new Theme("AI/반도체");
    setField(theme, "id", 1L);
    when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));
    StockTheme a = stockTheme(new Stock("005930", "삼성전자", Market.KOSPI), theme, 0.6);
    StockTheme b = stockTheme(new Stock("000660", "SK하이닉스", Market.KOSPI), theme, 0.5);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(stockThemeRepository.findByThemeOrderByMarketCap(
            eq(1L), eq((short) 1), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 2), 74));
    when(krxQuoteService.getQuotes(List.of("005930", "000660")))
        .thenReturn(List.of(new KrxQuote("005930", 230000, 2.22, 5000), KrxQuote.empty("000660")));

    ThemeStocksResponse res = service.stocks(1L, 1, 2);

    assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    assertThat(res.theme().stockCount()).isEqualTo(74);
    assertThat(res.items()).hasSize(2);
    assertThat(res.items().get(0).currentPrice()).isEqualTo(230000L);
    assertThat(res.items().get(0).probability()).isEqualTo(0.6);
    assertThat(res.items().get(1).currentPrice()).isNull();
    assertThat(res.pagination().totalPages()).isEqualTo(37);
    assertThat(res.pagination().hasNext()).isTrue();
  }

  @Test
  void stocks_size는_50으로_제한() {
    Theme theme = new Theme("AI/반도체");
    when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(stockThemeRepository.findByThemeOrderByMarketCap(
            eq(1L), eq((short) 1), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    when(krxQuoteService.getQuotes(any())).thenReturn(List.of());

    assertThat(service.stocks(1L, 1, 500).pagination().size()).isEqualTo(50);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  void stocks_없는_테마는_NOT_FOUND() {
    when(themeRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.stocks(99L, 1, 20)).isInstanceOf(DomainException.class);
    verifyNoInteractions(stockThemeRepository, krxQuoteService);
  }

  private static StockTheme stockTheme(Stock stock, Theme theme, double probability)
      throws Exception {
    StockTheme st = new StockTheme(stock, theme, (short) 1);
    st.setProbability(probability);
    return st;
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
