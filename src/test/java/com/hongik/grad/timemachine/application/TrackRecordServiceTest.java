package com.hongik.grad.timemachine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.timemachine.application.TrackRecordService.Judged;
import com.hongik.grad.timemachine.domain.PricePoint;
import com.hongik.grad.timemachine.domain.ReplayReportRepository;
import com.hongik.grad.timemachine.domain.ReplayStatus;
import com.hongik.grad.timemachine.dto.TrackRecord;
import com.hongik.grad.timemachine.dto.TrackRecordResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackRecordServiceTest {

  @Mock private ReplayReportRepository reportRepository;
  @Mock private PriceBackfillService priceBackfillService;
  @InjectMocks private TrackRecordService service;

  @Test
  @DisplayName("판정 수가 10 미만이면 hit_rate 는 null")
  void hitRateHiddenBelowTenJudged() {
    List<Judged> nine = new ArrayList<>(nCopies(6, ReplayStatus.HIT));
    nine.addAll(nCopies(3, ReplayStatus.MISS));
    nine.addAll(nCopies(2, ReplayStatus.NEUTRAL));

    TrackRecord r = TrackRecordService.tally(nine, null);

    assertThat(r).isEqualTo(new TrackRecord(11, 9, 6, null, 2, 0, 0));
  }

  @Test
  @DisplayName("판정 10 건 이상이면 hit_rate = hits / judged, 대기·자료부족은 분모에서 뺀다")
  void hitRateFromTenJudged() {
    List<Judged> all = new ArrayList<>(nCopies(7, ReplayStatus.HIT));
    all.addAll(nCopies(3, ReplayStatus.MISS));
    all.add(new Judged(DisclosureType.REPORT, ReplayStatus.PENDING));
    all.add(new Judged(DisclosureType.REPORT, ReplayStatus.INSUFFICIENT_DATA));

    TrackRecord r = TrackRecordService.tally(all, null);

    assertThat(r).isEqualTo(new TrackRecord(12, 10, 7, 0.7, 0, 1, 1));
  }

  @Test
  @DisplayName("type 으로 거르면 그 유형만 센다")
  void filtersByType() {
    List<Judged> all =
        List.of(
            new Judged(DisclosureType.EVENT, ReplayStatus.HIT),
            new Judged(DisclosureType.REPORT, ReplayStatus.MISS));

    assertThat(TrackRecordService.tally(all, DisclosureType.EVENT))
        .isEqualTo(new TrackRecord(1, 1, 1, null, 0, 0, 0));
  }

  @Test
  @DisplayName("보고서마다 판정해 전체·유형별 전적을 만들고 시세는 가장 오래된 공시 기준으로 한 번만 읽는다")
  void reportJudgesEachReport() throws Exception {
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    LocalDate base = LocalDate.of(2026, 1, 5);
    List<LocalDate> days = weekdays(base, 61);
    AnalysisReport hit = report(stock, DisclosureType.EVENT, base, "1.2");
    AnalysisReport neutral = report(stock, DisclosureType.REPORT, base, "0.1");
    when(reportRepository.findByTickerWithDisclosure("005930")).thenReturn(List.of(hit, neutral));
    List<PricePoint> stockPrices = new ArrayList<>();
    List<PricePoint> kospi = new ArrayList<>();
    for (int i = 0; i < days.size(); i++) {
      stockPrices.add(new PricePoint(days.get(i), i == 60 ? 1100 : 1000));
      kospi.add(new PricePoint(days.get(i), 100_000));
    }
    when(priceBackfillService.series(eq("005930"), eq(base))).thenReturn(stockPrices);
    when(priceBackfillService.series(eq("KOSPI"), eq(base))).thenReturn(kospi);
    when(priceBackfillService.today()).thenReturn(days.get(60));

    TrackRecordResponse r = service.report("005930", null);

    assertThat(r.record()).isEqualTo(new TrackRecord(2, 1, 1, null, 1, 0, 0));
    assertThat(r.byType()).hasSize(4);
    assertThat(r.byType().get(3).type()).isEqualTo("event");
    assertThat(r.byType().get(3).record().hits()).isEqualTo(1);
    assertThat(service.forTicker("005930", DisclosureType.REPORT).neutral()).isEqualTo(1);
  }

  @Test
  @DisplayName("보고서가 없으면 시세를 받지 않고 0 전적")
  void emptyWithoutReports() {
    when(reportRepository.findByTickerWithDisclosure("005930")).thenReturn(List.of());

    assertThat(service.forTicker("005930", null))
        .isEqualTo(new TrackRecord(0, 0, 0, null, 0, 0, 0));
    org.mockito.Mockito.verify(priceBackfillService, org.mockito.Mockito.never())
        .series(any(), any());
  }

  private static List<Judged> nCopies(int n, ReplayStatus s) {
    return Collections.nCopies(n, new Judged(DisclosureType.REPORT, s));
  }

  private static AnalysisReport report(
      Stock stock, DisclosureType type, LocalDate filed, String car) {
    Disclosure d =
        new Disclosure(
            stock,
            "x" + type + car,
            "t",
            type,
            OffsetDateTime.of(filed.atTime(18, 0), ZoneOffset.ofHours(9)));
    AnalysisReport r = new AnalysisReport(stock, new Theme("AI"), "t");
    r.setDisclosure(d);
    r.setExpectedCar(new BigDecimal(car));
    return r;
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
