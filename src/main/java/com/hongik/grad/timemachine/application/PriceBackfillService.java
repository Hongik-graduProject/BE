package com.hongik.grad.timemachine.application;

import com.hongik.grad.stocks.client.NaverDailyPriceClient;
import com.hongik.grad.stocks.client.NaverDailyPriceClient.DailyPrice;
import com.hongik.grad.timemachine.domain.DailyPriceRepository;
import com.hongik.grad.timemachine.domain.DailyPriceRepository.DateRange;
import com.hongik.grad.timemachine.domain.PricePoint;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 판정에 필요한 일별 종가를 daily_prices 에 지연 적재한다. 저장분이 {@code from} 을 덮고 충분히 최신이면 외부 호출을 하지 않는다.
 *
 * <p>shortcut: {@code from} 이전 시세가 원천에 없는 종목(신규 상장·1500 거래일보다 오래된 공시)이나 장기 거래정지 종목은 열 때마다 다시 받는다.
 * 호출량이 문제가 되면 마지막 시도 시각을 저장해 하루 한 번으로 제한한다.
 */
@Service
public class PriceBackfillService {

  static final String KOSPI = "KOSPI";
  private static final Logger log = LoggerFactory.getLogger(PriceBackfillService.class);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int MAX_DAYS = 1500;
  private static final int MARGIN_DAYS = 5;
  // 주말 + 하루 공휴일까지는 최신으로 본다.
  private static final int FRESH_LAG_DAYS = 4;
  // 60 거래일 창은 연휴를 넣어도 달력 100 일 안에 끝난다. 그 뒤 시세는 판정에 필요 없다.
  private static final int WINDOW_CALENDAR_DAYS = 100;
  // 기준일(from 이하 마지막 거래일)을 찾으려고 from 앞쪽을 조금 더 읽는다.
  private static final int LOOKBACK_DAYS = 14;

  private final NaverDailyPriceClient naverDailyPriceClient;
  private final DailyPriceRepository dailyPriceRepository;
  private final Clock clock;

  public PriceBackfillService(
      NaverDailyPriceClient naverDailyPriceClient,
      DailyPriceRepository dailyPriceRepository,
      Clock clock) {
    this.naverDailyPriceClient = naverDailyPriceClient;
    this.dailyPriceRepository = dailyPriceRepository;
    this.clock = clock;
  }

  /** {@code from} 부터 판정 창 끝(또는 오늘)까지 시세가 없을 때만 최근 N 거래일을 받아 넣는다. */
  public void ensure(String ticker, LocalDate from) {
    LocalDate today = today();
    LocalDate needUntil = min(today.minusDays(FRESH_LAG_DAYS), from.plusDays(WINDOW_CALENDAR_DAYS));
    DateRange have = dailyPriceRepository.range(ticker);
    if (have != null && !have.min().isAfter(from) && !have.max().isBefore(needUntil)) return;

    int days = (int) Math.min(MAX_DAYS, weekdaysBetween(from, today) + MARGIN_DAYS);
    List<DailyPrice> fetched =
        KOSPI.equals(ticker)
            ? naverDailyPriceClient.fetchKospi(days)
            : naverDailyPriceClient.fetch(ticker, days);
    List<PricePoint> rows =
        fetched.stream()
            .filter(p -> p.close() > 0)
            .map(p -> new PricePoint(p.date(), p.close()))
            .toList();
    if (rows.isEmpty()) {
      log.warn("Price backfill got no rows for {} (requested {} days)", ticker, days);
      return;
    }
    dailyPriceRepository.insertIgnore(ticker, rows);
  }

  /** ensure 후 기준일 탐색에 필요한 앞쪽 여유를 포함해 오름차순으로 읽는다. */
  public List<PricePoint> series(String ticker, LocalDate from) {
    ensure(ticker, from);
    return dailyPriceRepository.findFrom(ticker, from.minusDays(LOOKBACK_DAYS));
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(KST));
  }

  static long weekdaysBetween(LocalDate from, LocalDate to) {
    if (!from.isBefore(to)) return 0;
    return from.datesUntil(to.plusDays(1))
        .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY)
        .filter(d -> d.getDayOfWeek() != DayOfWeek.SUNDAY)
        .count();
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }
}
