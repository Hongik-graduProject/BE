package com.hongik.grad.timemachine.application;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.timemachine.domain.PricePoint;
import com.hongik.grad.timemachine.domain.ReplayReportRepository;
import com.hongik.grad.timemachine.domain.ReplayStatus;
import com.hongik.grad.timemachine.dto.TrackRecord;
import com.hongik.grad.timemachine.dto.TrackRecordResponse;
import com.hongik.grad.timemachine.dto.TrackRecordResponse.TypeTrackRecord;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/** 종목 보고서 전부를 되감아 전적을 센다. 반박 모드(PAST_MISSES)도 {@link #forTicker} 를 쓴다. */
@Service
public class TrackRecordService {

  static final int MIN_JUDGED_FOR_RATE = 10;

  private final ReplayReportRepository replayReportRepository;
  private final PriceBackfillService priceBackfillService;

  public TrackRecordService(
      ReplayReportRepository replayReportRepository, PriceBackfillService priceBackfillService) {
    this.replayReportRepository = replayReportRepository;
    this.priceBackfillService = priceBackfillService;
  }

  public TrackRecord forTicker(String ticker, DisclosureType typeOrNull) {
    return tally(judgeAll(ticker), typeOrNull);
  }

  /** by_type 은 필터와 상관없이 네 유형 모두 담는다. */
  public TrackRecordResponse report(String ticker, DisclosureType typeOrNull) {
    List<Judged> all = judgeAll(ticker);
    List<TypeTrackRecord> byType =
        Arrays.stream(DisclosureType.values())
            .map(t -> new TypeTrackRecord(t.getValue(), tally(all, t)))
            .toList();
    return new TrackRecordResponse(
        ticker, typeOrNull == null ? null : typeOrNull.getValue(), tally(all, typeOrNull), byType);
  }

  private List<Judged> judgeAll(String ticker) {
    List<AnalysisReport> reports = replayReportRepository.findByTickerWithDisclosure(ticker);
    if (reports.isEmpty()) return List.of();
    // 가장 오래된 공시부터 한 번에 받아 두고 보고서마다 같은 시계열로 판정한다 (정렬: filed_at 오름차순).
    LocalDate earliest = ReplayService.asOf(reports.get(0).getDisclosure());
    List<PricePoint> stock = priceBackfillService.series(ticker, earliest);
    List<PricePoint> kospi = priceBackfillService.series(PriceBackfillService.KOSPI, earliest);
    LocalDate today = priceBackfillService.today();
    return reports.stream()
        .map(
            r ->
                new Judged(
                    r.getDisclosure().getType(),
                    ReplayJudge.judge(
                            r.getExpectedCar(),
                            ReplayService.asOf(r.getDisclosure()),
                            stock,
                            kospi,
                            today)
                        .status()))
        .toList();
  }

  static TrackRecord tally(List<Judged> judged, DisclosureType typeOrNull) {
    int total = 0, hits = 0, misses = 0, neutral = 0, pending = 0, insufficient = 0;
    for (Judged j : judged) {
      if (typeOrNull != null && j.type() != typeOrNull) continue;
      total++;
      switch (j.status()) {
        case HIT -> hits++;
        case MISS -> misses++;
        case NEUTRAL -> neutral++;
        case PENDING -> pending++;
        case INSUFFICIENT_DATA -> insufficient++;
      }
    }
    int judgedCount = hits + misses;
    Double hitRate = judgedCount < MIN_JUDGED_FOR_RATE ? null : (double) hits / judgedCount;
    return new TrackRecord(total, judgedCount, hits, hitRate, neutral, pending, insufficient);
  }

  record Judged(DisclosureType type, ReplayStatus status) {}
}
