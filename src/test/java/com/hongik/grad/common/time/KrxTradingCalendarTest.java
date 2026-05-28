package com.hongik.grad.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KrxTradingCalendarTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private KrxTradingCalendar calendarAt(LocalDate date, LocalTime time) {
    ZonedDateTime zdt = ZonedDateTime.of(date, time, KST);
    Clock fixed = Clock.fixed(zdt.toInstant(), ZoneOffset.UTC);
    return new KrxTradingCalendar(fixed);
  }

  @Test
  @DisplayName("평일 장중(월요일 10:00 KST)이면 true를 반환한다")
  void weekday_duringMarketHours_returnsTrue() {
    // 2026-05-25 is Monday
    KrxTradingCalendar calendar = calendarAt(LocalDate.of(2026, 5, 25), LocalTime.of(10, 0));

    assertThat(calendar.isMarketOpen()).isTrue();
  }

  @Test
  @DisplayName("평일 장외(월요일 07:00 KST)이면 false를 반환한다")
  void weekday_offHours_returnsFalse() {
    KrxTradingCalendar calendar = calendarAt(LocalDate.of(2026, 5, 25), LocalTime.of(7, 0));

    assertThat(calendar.isMarketOpen()).isFalse();
  }

  @Test
  @DisplayName("주말(토요일)이면 false를 반환한다")
  void weekend_returnsFalse() {
    // 2026-05-23 is Saturday
    KrxTradingCalendar calendar = calendarAt(LocalDate.of(2026, 5, 23), LocalTime.of(10, 0));

    assertThat(calendar.isMarketOpen()).isFalse();
  }

  @Test
  @DisplayName("경계: 정확히 09:00 KST이면 true를 반환한다")
  void boundary_exactlyOpen_returnsTrue() {
    KrxTradingCalendar calendar = calendarAt(LocalDate.of(2026, 5, 25), LocalTime.of(9, 0));

    assertThat(calendar.isMarketOpen()).isTrue();
  }

  @Test
  @DisplayName("경계: 정확히 15:30 KST이면 false를 반환한다")
  void boundary_exactlyClose_returnsFalse() {
    KrxTradingCalendar calendar = calendarAt(LocalDate.of(2026, 5, 25), LocalTime.of(15, 30));

    assertThat(calendar.isMarketOpen()).isFalse();
  }
}
