package com.hongik.grad.timemachine.application;

import com.hongik.grad.timemachine.domain.PricePoint;
import com.hongik.grad.timemachine.domain.ReplayStatus;
import com.hongik.grad.timemachine.dto.ReplayResponse.Point;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 적중 판정 (구현 계획 §3-4). KOSPI 가 거래한 날을 거래일 달력으로 쓴다. 그날 종목 시세가 없으면 거래정지로 본다.
 *
 * <ul>
 *   <li>기준일: as_of 이하의 마지막 거래일.
 *   <li>종료일: 기준일 이후 60 번째 거래일.
 *   <li>초과수익률(%) = (종목 종료/기준 − KOSPI 종료/기준) × 100.
 *   <li>판정 우선순위: NEUTRAL → INSUFFICIENT_DATA → PENDING → HIT/MISS. 초과수익률이 정확히 0 이면 MISS.
 * </ul>
 */
final class ReplayJudge {

  static final int WINDOW = 60;
  static final BigDecimal NEUTRAL_BAND = new BigDecimal("0.5");
  // KOSPI 시세가 이보다 오래됐으면 "아직 안 지남" 이 아니라 적재 실패로 본다 (연휴 최장 ~5 영업일 + 여유).
  static final int STALE_DAYS = 10;

  private ReplayJudge() {}

  record Result(
      ReplayStatus status,
      LocalDate baseDate,
      LocalDate endDate,
      Double excessReturnPct,
      Integer daysRemaining,
      List<Point> series) {}

  static Result judge(
      BigDecimal expectedCar,
      LocalDate asOf,
      List<PricePoint> stock,
      List<PricePoint> kospi,
      LocalDate today) {
    Map<LocalDate, Long> stockClose = new HashMap<>();
    LocalDate stockLast = null;
    for (PricePoint p : stock) {
      if (p.close() <= 0) continue;
      stockClose.put(p.date(), p.close());
      if (stockLast == null || p.date().isAfter(stockLast)) stockLast = p.date();
    }
    List<PricePoint> days =
        kospi.stream()
            .filter(p -> p.close() > 0)
            .sorted(Comparator.comparing(PricePoint::date))
            .toList();

    int baseIdx = -1;
    for (int i = 0; i < days.size() && !days.get(i).date().isAfter(asOf); i++) baseIdx = i;
    boolean neutral = expectedCar != null && expectedCar.abs().compareTo(NEUTRAL_BAND) < 0;
    if (baseIdx < 0 || stockClose.get(days.get(baseIdx).date()) == null) {
      return new Result(
          neutral ? ReplayStatus.NEUTRAL : ReplayStatus.INSUFFICIENT_DATA,
          null,
          null,
          null,
          null,
          List.of());
    }

    PricePoint kBase = days.get(baseIdx);
    long sBase = stockClose.get(kBase.date());
    List<PricePoint> window = days.subList(baseIdx, Math.min(days.size(), baseIdx + WINDOW + 1));
    boolean complete = window.size() == WINDOW + 1;

    List<Point> series = new ArrayList<>();
    boolean halted = false;
    for (PricePoint k : window) {
      Long s = stockClose.get(k.date());
      if (s == null) {
        // 창이 덜 찼고 종목 시세만 늦게 적재된 날이면 정지가 아니라 아직 모르는 날이다.
        halted = complete || !k.date().isAfter(stockLast);
        break;
      }
      series.add(new Point(k.date(), pct(s, sBase), pct(k.close(), kBase.close())));
    }

    LocalDate endDate = complete ? window.get(WINDOW).date() : null;
    Double excess = null;
    if (complete && !halted) {
      Point end = series.get(WINDOW);
      excess = round(end.stockReturnPct() - end.kospiReturnPct());
    }

    ReplayStatus status;
    Integer daysRemaining = null;
    if (expectedCar == null) {
      status = ReplayStatus.INSUFFICIENT_DATA;
    } else if (neutral) {
      status = ReplayStatus.NEUTRAL;
    } else if (halted) {
      status = ReplayStatus.INSUFFICIENT_DATA;
    } else if (!complete) {
      LocalDate kospiLast = days.get(days.size() - 1).date();
      if (kospiLast.isBefore(today.minusDays(STALE_DAYS))) {
        status = ReplayStatus.INSUFFICIENT_DATA;
      } else {
        status = ReplayStatus.PENDING;
        daysRemaining = WINDOW - (window.size() - 1);
      }
    } else {
      status = Math.signum(excess) == expectedCar.signum() ? ReplayStatus.HIT : ReplayStatus.MISS;
    }
    return new Result(status, kBase.date(), endDate, excess, daysRemaining, series);
  }

  private static double pct(long value, long base) {
    return round(((double) value / base - 1) * 100);
  }

  private static double round(double v) {
    return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
  }
}
