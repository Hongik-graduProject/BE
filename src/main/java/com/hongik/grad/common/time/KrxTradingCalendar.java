package com.hongik.grad.common.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class KrxTradingCalendar {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
  private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

  private final Clock clock;

  public KrxTradingCalendar(Clock clock) {
    this.clock = clock;
  }

  public boolean isMarketOpen() {
    ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(KST);
    DayOfWeek day = now.getDayOfWeek();
    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
      return false;
    }
    LocalTime time = now.toLocalTime();
    return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
  }
}
