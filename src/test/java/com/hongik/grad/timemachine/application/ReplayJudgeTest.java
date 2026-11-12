package com.hongik.grad.timemachine.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.timemachine.domain.PricePoint;
import com.hongik.grad.timemachine.domain.ReplayStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplayJudgeTest {

  // 2026-01-05 은 월요일. 기준일 = days[0].
  private static final LocalDate START = LocalDate.of(2026, 1, 5);
  private static final List<LocalDate> DAYS = weekdays(START, 80);
  private static final BigDecimal UP = new BigDecimal("1.0000");

  @Test
  @DisplayName("기준일 이후 정확히 60 번째 거래일이 종료일이고, 종목이 KOSPI 를 이기면 상승 예측은 HIT")
  void hitOnExactlySixtiethDay() {
    ReplayJudge.Result r =
        judge(UP, START, prices(61, i -> i == 60 ? 1100 : 1000), prices(61, i -> 100_000), 60);

    assertThat(r.status()).isEqualTo(ReplayStatus.HIT);
    assertThat(r.baseDate()).isEqualTo(DAYS.get(0));
    assertThat(r.endDate()).isEqualTo(DAYS.get(60));
    assertThat(r.excessReturnPct()).isEqualTo(10.0);
    assertThat(r.daysRemaining()).isNull();
    assertThat(r.series()).hasSize(61);
    assertThat(r.series().get(0).stockReturnPct()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("61 번째 이후 시세는 판정에 쓰지 않는다")
  void ignoresPricesAfterWindow() {
    ReplayJudge.Result r =
        judge(UP, START, prices(80, i -> i > 60 ? 500 : 1100), prices(80, i -> 100_000), 79);

    assertThat(r.endDate()).isEqualTo(DAYS.get(60));
    assertThat(r.series()).hasSize(61);
  }

  @Test
  @DisplayName("절대 수익률이 아니라 KOSPI 대비 초과수익률 부호로 판정한다")
  void judgesOnExcessReturnNotAbsolute() {
    // 종목 +5%, KOSPI +10% → 초과 -5%p. 상승 예측은 MISS, 하락 예측은 HIT.
    List<PricePoint> stock = prices(61, i -> i == 60 ? 1050 : 1000);
    List<PricePoint> kospi = prices(61, i -> i == 60 ? 110_000 : 100_000);

    ReplayJudge.Result up = judge(UP, START, stock, kospi, 60);
    ReplayJudge.Result down = judge(new BigDecimal("-0.8"), START, stock, kospi, 60);

    assertThat(up.excessReturnPct()).isEqualTo(-5.0);
    assertThat(up.status()).isEqualTo(ReplayStatus.MISS);
    assertThat(down.status()).isEqualTo(ReplayStatus.HIT);
  }

  @Test
  @DisplayName("59 거래일만 지났으면 PENDING, days_remaining = 1")
  void pendingWithDaysRemaining() {
    ReplayJudge.Result r = judge(UP, START, prices(60, i -> 1000), prices(60, i -> 100_000), 59);

    assertThat(r.status()).isEqualTo(ReplayStatus.PENDING);
    assertThat(r.daysRemaining()).isEqualTo(1);
    assertThat(r.endDate()).isNull();
    assertThat(r.excessReturnPct()).isNull();
    assertThat(r.series()).hasSize(60);
  }

  @Test
  @DisplayName("창이 덜 찼는데 종목 시세만 늦게 적재됐으면 거래정지가 아니라 PENDING")
  void pendingWhenStockDataLagsIndex() {
    ReplayJudge.Result r = judge(UP, START, prices(28, i -> 1000), prices(30, i -> 100_000), 29);

    assertThat(r.status()).isEqualTo(ReplayStatus.PENDING);
    assertThat(r.daysRemaining()).isEqualTo(31);
    assertThat(r.series()).hasSize(28);
  }

  @Test
  @DisplayName("KOSPI 시세가 오래 멈춰 있으면 대기가 아니라 자료 부족")
  void staleIndexIsInsufficient() {
    ReplayJudge.Result r =
        ReplayJudge.judge(
            UP,
            START,
            prices(30, i -> 1000),
            prices(30, i -> 100_000),
            DAYS.get(29).plusDays(ReplayJudge.STALE_DAYS + 1));

    assertThat(r.status()).isEqualTo(ReplayStatus.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("|expected_car| < 0.5 는 NEUTRAL, 정확히 0.5 는 판정한다")
  void neutralThreshold() {
    List<PricePoint> stock = prices(61, i -> i == 60 ? 1100 : 1000);
    List<PricePoint> kospi = prices(61, i -> 100_000);

    assertThat(judge(new BigDecimal("0.4999"), START, stock, kospi, 60).status())
        .isEqualTo(ReplayStatus.NEUTRAL);
    assertThat(judge(new BigDecimal("-0.4999"), START, stock, kospi, 60).status())
        .isEqualTo(ReplayStatus.NEUTRAL);
    assertThat(judge(new BigDecimal("-0.5"), START, stock, kospi, 60).status())
        .isEqualTo(ReplayStatus.MISS);
    // 중립이어도 차트용 시계열과 초과수익률은 채운다.
    assertThat(judge(new BigDecimal("0.1"), START, stock, kospi, 60).excessReturnPct())
        .isEqualTo(10.0);
  }

  @Test
  @DisplayName("창 중간에 종목 시세가 빠지면(거래정지) 자료 부족, 시계열은 정지 직전까지")
  void haltedMidWindowIsInsufficient() {
    List<PricePoint> stock = new ArrayList<>(prices(61, i -> 1000));
    stock.remove(30);

    ReplayJudge.Result r = judge(UP, START, stock, prices(61, i -> 100_000), 60);

    assertThat(r.status()).isEqualTo(ReplayStatus.INSUFFICIENT_DATA);
    assertThat(r.excessReturnPct()).isNull();
    assertThat(r.series()).hasSize(30);
  }

  @Test
  @DisplayName("시세가 없거나 기준일 시세가 없으면 자료 부족, expected_car 가 null 이어도 자료 부족")
  void missingDataIsInsufficient() {
    List<PricePoint> kospi = prices(61, i -> 100_000);
    assertThat(judge(UP, START, List.of(), kospi, 60).status())
        .isEqualTo(ReplayStatus.INSUFFICIENT_DATA);
    assertThat(judge(UP, START, prices(61, i -> 1000), List.of(), 60).status())
        .isEqualTo(ReplayStatus.INSUFFICIENT_DATA);
    assertThat(judge(UP, START.minusDays(1), prices(61, i -> 1000), kospi, 60).status())
        .isEqualTo(ReplayStatus.INSUFFICIENT_DATA);
    assertThat(judge(null, START, prices(61, i -> 1000), kospi, 60).status())
        .isEqualTo(ReplayStatus.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("주말 공시는 직전 금요일 종가를 기준으로 삼는다")
  void weekendFilingUsesPreviousTradingDay() {
    LocalDate saturday = DAYS.get(4).plusDays(1);
    assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);

    ReplayJudge.Result r = judge(UP, saturday, prices(80, i -> 1000), prices(80, i -> 100_000), 79);

    assertThat(r.baseDate()).isEqualTo(DAYS.get(4));
    assertThat(r.endDate()).isEqualTo(DAYS.get(64));
  }

  private static ReplayJudge.Result judge(
      BigDecimal car,
      LocalDate asOf,
      List<PricePoint> stock,
      List<PricePoint> kospi,
      int todayIdx) {
    return ReplayJudge.judge(car, asOf, stock, kospi, DAYS.get(todayIdx));
  }

  private static List<PricePoint> prices(int n, IntToLongFunction close) {
    List<PricePoint> out = new ArrayList<>();
    for (int i = 0; i < n; i++) out.add(new PricePoint(DAYS.get(i), close.applyAsLong(i)));
    return out;
  }

  private static List<LocalDate> weekdays(LocalDate start, int n) {
    List<LocalDate> out = new ArrayList<>();
    for (LocalDate d = start; out.size() < n; d = d.plusDays(1)) {
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
        out.add(d);
      }
    }
    return out;
  }
}
