package com.hongik.grad.stocks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.disclosures.client.DartCorpCodeClient;
import com.hongik.grad.disclosures.client.DartCorpCodeClient.CorpInfo;
import com.hongik.grad.stocks.client.NaverMarketListClient;
import com.hongik.grad.stocks.client.NaverMarketListClient.Listing;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.stocks.event.StocksBootstrappedEvent;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CorpCodeBootstrapTest {

  @Mock private StockRepository stockRepository;
  @Mock private DartCorpCodeClient corpCodeClient;
  @Mock private NaverMarketListClient marketListClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  private CorpCodeBootstrap bootstrap;

  @BeforeEach
  void setUp() {
    bootstrap =
        new CorpCodeBootstrap(stockRepository, corpCodeClient, marketListClient, eventPublisher);
  }

  @Test
  @DisplayName("네이버 목록으로 KOSPI/KOSDAQ 을 정하고, 목록에 없는 새 종목은 넣지 않으며 기존 종목은 지우지 않는다")
  void classifiesMarketsAndDropsOthers() {
    Stock konexExisting = new Stock("333333", "코넥스사", Market.KOSPI);
    Stock kosdaqExisting = new Stock("044340", "위닉스", Market.KOSPI);
    when(corpCodeClient.fetchAll())
        .thenReturn(
            Map.of(
                "005930", new CorpInfo("00126380", "삼성전자"),
                "044340", new CorpInfo("00293886", "위닉스"),
                "333333", new CorpInfo("00999999", "코넥스사"),
                "444444", new CorpInfo("00888888", "새코넥스")));
    when(marketListClient.fetchAll())
        .thenReturn(
            Map.of(
                "005930",
                new Listing(Market.KOSPI, 1_344_644_079_840_000L),
                "044340",
                new Listing(Market.KOSDAQ, null)));
    when(stockRepository.findById("005930")).thenReturn(Optional.empty());
    when(stockRepository.findById("044340")).thenReturn(Optional.of(kosdaqExisting));
    when(stockRepository.findById("333333")).thenReturn(Optional.of(konexExisting));
    when(stockRepository.findById("444444")).thenReturn(Optional.empty());

    bootstrap.bootstrap();

    ArgumentCaptor<Stock> saved = ArgumentCaptor.forClass(Stock.class);
    verify(stockRepository).save(saved.capture());
    assertThat(saved.getValue().getTicker()).isEqualTo("005930");
    assertThat(saved.getValue().getMarket()).isEqualTo(Market.KOSPI);
    assertThat(saved.getValue().getMarketCap()).isEqualTo(1_344_644_079_840_000L);
    assertThat(kosdaqExisting.getMarket()).isEqualTo(Market.KOSDAQ);
    verify(stockRepository, never()).delete(any());
    assertThat(konexExisting.getMarket()).isEqualTo(Market.KOSPI);
    verify(eventPublisher).publishEvent(any(StocksBootstrappedEvent.class));
  }

  @Test
  @DisplayName("네이버 목록을 못 받으면 새 종목을 넣지 않고 기존 종목도 그대로 둔다")
  void marketListUnavailable_keepsEverything() {
    Stock existing = new Stock("044340", "위닉스", Market.KOSDAQ);
    when(corpCodeClient.fetchAll())
        .thenReturn(
            Map.of(
                "005930", new CorpInfo("00126380", "삼성전자"),
                "044340", new CorpInfo("00293886", "위닉스")));
    when(marketListClient.fetchAll()).thenReturn(Map.of());
    when(stockRepository.findById("005930")).thenReturn(Optional.empty());
    when(stockRepository.findById("044340")).thenReturn(Optional.of(existing));

    bootstrap.bootstrap();

    verify(stockRepository, never()).delete(any());
    verify(stockRepository, never()).save(any());
    assertThat(existing.getMarket()).isEqualTo(Market.KOSDAQ);
    verify(eventPublisher).publishEvent(any(StocksBootstrappedEvent.class));
  }

  @Test
  @DisplayName("DART 호출이 예외를 던져도 적재 완료 이벤트는 나간다")
  void publishesEventEvenOnFailure() {
    when(corpCodeClient.fetchAll()).thenThrow(new IllegalStateException("boom"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> bootstrap.bootstrap())
        .isInstanceOf(IllegalStateException.class);
    verify(eventPublisher).publishEvent(any(StocksBootstrappedEvent.class));
  }
}
