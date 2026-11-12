package com.hongik.grad.timemachine.application;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.timemachine.domain.ReplayReportRepository;
import com.hongik.grad.timemachine.dto.ReplayResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class ReplayService {

  static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ReplayReportRepository replayReportRepository;
  private final PriceBackfillService priceBackfillService;

  public ReplayService(
      ReplayReportRepository replayReportRepository, PriceBackfillService priceBackfillService) {
    this.replayReportRepository = replayReportRepository;
    this.priceBackfillService = priceBackfillService;
  }

  public ReplayResponse replay(Long reportId) {
    AnalysisReport report =
        replayReportRepository
            .findWithDisclosure(reportId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "보고서를 찾을 수 없습니다"));
    Disclosure disclosure = report.getDisclosure();
    if (disclosure == null) {
      throw new DomainException(ErrorCode.NOT_FOUND, "되감을 공시가 연결되지 않은 보고서입니다");
    }
    String ticker = report.getStock().getTicker();
    LocalDate asOf = asOf(disclosure);
    ReplayJudge.Result r =
        ReplayJudge.judge(
            report.getExpectedCar(),
            asOf,
            priceBackfillService.series(ticker, asOf),
            priceBackfillService.series(PriceBackfillService.KOSPI, asOf),
            priceBackfillService.today());
    return new ReplayResponse(
        report.getId(),
        ticker,
        disclosure.getType().getValue(),
        asOf,
        r.baseDate(),
        r.endDate(),
        report.getExpectedCar() == null ? null : report.getExpectedCar().doubleValue(),
        r.excessReturnPct(),
        r.status().name(),
        r.daysRemaining(),
        r.series());
  }

  static LocalDate asOf(Disclosure disclosure) {
    return disclosure.getFiledAt().atZoneSameInstant(KST).toLocalDate();
  }
}
