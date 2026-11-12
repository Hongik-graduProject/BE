package com.hongik.grad.timemachine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hongik.grad.stocks.client.NaverDailyPriceClient;
import com.hongik.grad.stocks.client.NaverDailyPriceClient.DailyPrice;
import com.hongik.grad.timemachine.domain.DailyPriceRepository;
import com.hongik.grad.timemachine.domain.DailyPriceRepository.DateRange;
import com.hongik.grad.timemachine.domain.PricePoint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceBackfillServiceTest {

  // 2026-11-12 (목) KST
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

  @Mock private NaverDailyPriceClient client;
  @Mock private DailyPriceRepository repository;
  private PriceBackfillService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC);
    service = new PriceBackfillService(client, repository, clock);
  }

  @Test
  @DisplayName("저장분이 from 을 덮고 최신이면 외부 호출하지 않는다")
  void skipsWhenCovered() {
    when(repository.range("005930"))
        .thenReturn(new DateRange(LocalDate.of(2026, 9, 1), TODAY.minusDays(1)));

    service.ensure("005930", LocalDate.of(2026, 10, 1));

    verifyNoInteractions(client);
  }

  @Test
  @DisplayName("판정 창(달력 100 일)이 이미 끝난 과거 공시는 최근 시세가 없어도 다시 받지 않는다")
  void skipsOldWindowWithoutRecentPrices() {
    when(repository.range("005930"))
        .thenReturn(new DateRange(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 6, 30)));

    service.ensure("005930", LocalDate.of(2025, 1, 10));

    verifyNoInteractions(client);
  }

  @Test
  @DisplayName("저장분이 없으면 from 부터 오늘까지 평일 수 + 5 만큼 받아 0 이하 종가는 빼고 넣는다")
  void fetchesWhenMissing() {
    when(repository.range("005930")).thenReturn(null);
    LocalDate from = LocalDate.of(2026, 11, 2); // 월요일. 11-02 ~ 11-12 평일 9 일
    when(client.fetch("005930", 14))
        .thenReturn(
            List.of(
                new DailyPrice(TODAY, 1000, 0, 0, 0, 0, 0),
                new DailyPrice(TODAY.minusDays(1), 0, 0, 0, 0, 0, 0)));

    service.ensure("005930", from);

    verify(repository).insertIgnore("005930", List.of(new PricePoint(TODAY, 1000)));
  }

  @Test
  @DisplayName("KOSPI 는 지수 경로로 받고, 요청 거래일 수는 1500 을 넘지 않는다")
  void kospiUsesIndexEndpointAndCap() {
    when(repository.range("KOSPI")).thenReturn(null);
    when(client.fetchKospi(1500)).thenReturn(List.of());

    service.ensure("KOSPI", LocalDate.of(2010, 1, 4));

    verify(client, never()).fetch(any(), anyInt());
    verify(repository, never()).insertIgnore(eq("KOSPI"), any());
  }

  @Test
  @DisplayName("평일 수 계산")
  void countsWeekdays() {
    assertThat(PriceBackfillService.weekdaysBetween(LocalDate.of(2026, 11, 2), TODAY)).isEqualTo(9);
    assertThat(PriceBackfillService.weekdaysBetween(TODAY, TODAY)).isZero();
  }
}
