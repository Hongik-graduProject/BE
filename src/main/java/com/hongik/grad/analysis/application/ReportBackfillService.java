package com.hongik.grad.analysis.application;

import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.stocks.application.FinancialStatementFetchService;
import com.hongik.grad.themes.domain.StockThemeRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 보고서 목록을 열 때 그 종목의 최근 공시 중 보고서가 없는 것을 만든다.
 *
 * <p>공시 수집 직후 이벤트 경로({@code DisclosureCreatedListener})는 재무 데이터가 없으면 건너뛴다. 첫 수집 때 공시 수천 건마다 재무를 받으면
 * DART 일일 한도를 넘기 때문이다. 대신 사용자가 실제로 여는 종목만 여기서 재무를 받고 보고서를 만든다.
 */
@Service
public class ReportBackfillService {

  private static final Logger log = LoggerFactory.getLogger(ReportBackfillService.class);
  static final int RECENT_DISCLOSURES = 5;

  private final StockThemeRepository stockThemeRepository;
  private final DisclosureRepository disclosureRepository;
  private final AnalysisReportRepository analysisReportRepository;
  private final FinancialStatementFetchService financialStatementFetchService;
  private final AnalysisReportGenerator analysisReportGenerator;

  public ReportBackfillService(
      StockThemeRepository stockThemeRepository,
      DisclosureRepository disclosureRepository,
      AnalysisReportRepository analysisReportRepository,
      FinancialStatementFetchService financialStatementFetchService,
      AnalysisReportGenerator analysisReportGenerator) {
    this.stockThemeRepository = stockThemeRepository;
    this.disclosureRepository = disclosureRepository;
    this.analysisReportRepository = analysisReportRepository;
    this.financialStatementFetchService = financialStatementFetchService;
    this.analysisReportGenerator = analysisReportGenerator;
  }

  /** 보고서를 하나라도 새로 만들었으면 true. */
  public boolean ensureRecentReports(String ticker) {
    if (stockThemeRepository.findByStockTickerOrderByRankAsc(ticker).isEmpty()) {
      return false;
    }
    List<Disclosure> missing =
        disclosureRepository
            .findByStockTickerOrderByFiledAtDesc(ticker, PageRequest.of(0, RECENT_DISCLOSURES))
            .stream()
            .filter(
                d ->
                    analysisReportRepository
                        .findByStockTickerAndDisclosureId(ticker, d.getId())
                        .isEmpty())
            .toList();
    if (missing.isEmpty()) {
      return false;
    }
    boolean created = false;
    try {
      financialStatementFetchService.refreshIfMissing(ticker);
      for (Disclosure d : missing) {
        created |= analysisReportGenerator.generate(d.getId()).isPresent();
      }
    } catch (RuntimeException ex) {
      // 목록 조회는 실패시키지 않는다. 다음에 열 때 다시 시도한다.
      log.warn("Report backfill failed for ticker={}: {}", ticker, ex.getMessage(), ex);
    }
    return created;
  }
}
