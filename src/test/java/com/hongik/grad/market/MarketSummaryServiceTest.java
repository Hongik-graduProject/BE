package com.hongik.grad.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureRepository.TickerDisclosureCount;
import com.hongik.grad.market.application.MarketSummaryService;
import com.hongik.grad.market.client.NaverIndexClient;
import com.hongik.grad.market.dto.MarketIndexResponse;
import com.hongik.grad.market.dto.MarketSummaryResponse;
import com.hongik.grad.stocks.domain.Market;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MarketSummaryServiceTest {

  @Mock private NaverIndexClient naverIndexClient;
  @Mock private DisclosureRepository disclosureRepository;

  private MarketSummaryService service;

  @BeforeEach
  void setUp() {
    // UTC 11-02 16:00 = KST 11-03 01:00 → "오늘"은 KST 기준 11-03.
    Clock clock = Clock.fixed(Instant.parse("2026-11-02T16:00:00Z"), ZoneOffset.UTC);
    service = new MarketSummaryService(naverIndexClient, disclosureRepository, clock);
  }

  @Test
  void 지수_실패는_빼고_공시_집계는_KST_날짜로() {
    MarketIndexResponse kospi = new MarketIndexResponse("KOSPI", "코스피", 5859.08, 128.34, 2.24);
    when(naverIndexClient.fetch("KOSPI", "코스피")).thenReturn(Optional.of(kospi));
    when(naverIndexClient.fetch("KOSDAQ", "코스닥")).thenReturn(Optional.empty());

    // UTC 11-01 15:30 = KST 11-02 00:30
    Disclosure latest = mock(Disclosure.class);
    when(latest.getFiledAt()).thenReturn(OffsetDateTime.parse("2026-11-01T15:30:00Z"));
    when(disclosureRepository.findTopByOrderByFiledAtDesc()).thenReturn(Optional.of(latest));
    when(disclosureRepository.countByFiledAtGreaterThanEqualAndFiledAtLessThan(
            OffsetDateTime.parse("2026-11-02T00:00+09:00"),
            OffsetDateTime.parse("2026-11-03T00:00+09:00")))
        .thenReturn(399L);

    TickerDisclosureCount row = mock(TickerDisclosureCount.class);
    when(row.getTicker()).thenReturn("005930");
    when(row.getName()).thenReturn("삼성전자");
    when(row.getMarket()).thenReturn(Market.KOSPI);
    when(row.getDisclosureCount()).thenReturn(7L);
    when(disclosureRepository.findTopTickersByDisclosureCountSince(
            eq(OffsetDateTime.parse("2026-10-28T00:00+09:00")), eq(PageRequest.of(0, 5))))
        .thenReturn(List.of(row));

    MarketSummaryResponse res = service.summary();

    assertThat(res.indices()).containsExactly(kospi);
    assertThat(res.latestDisclosureDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(res.latestDisclosureCount()).isEqualTo(399L);
    assertThat(res.hotStocks()).hasSize(1);
    assertThat(res.hotStocks().get(0).ticker()).isEqualTo("005930");
    assertThat(res.hotStocks().get(0).market()).isEqualTo("KOSPI");
    assertThat(res.hotStocks().get(0).disclosureCount()).isEqualTo(7L);
  }

  @Test
  void 공시가_없으면_latest_는_null() {
    when(naverIndexClient.fetch(any(), any())).thenReturn(Optional.empty());
    when(disclosureRepository.findTopByOrderByFiledAtDesc()).thenReturn(Optional.empty());
    when(disclosureRepository.findTopTickersByDisclosureCountSince(any(), any()))
        .thenReturn(List.of());

    MarketSummaryResponse res = service.summary();

    assertThat(res.indices()).isEmpty();
    assertThat(res.latestDisclosureDate()).isNull();
    assertThat(res.latestDisclosureCount()).isNull();
    assertThat(res.hotStocks()).isEmpty();
    verify(disclosureRepository, never())
        .countByFiledAtGreaterThanEqualAndFiledAtLessThan(any(), any());
  }
}
