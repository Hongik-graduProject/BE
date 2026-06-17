package com.hongik.grad.stocks.application;

import com.hongik.grad.disclosures.client.DartFinanceClient;
import com.hongik.grad.disclosures.client.DartFinanceClient.FinanceRow;
import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 종목 상세 finance 탭 호출 시 lazy fetch — DART 연결 재무제표. */
@Service
public class FinancialStatementFetchService {

  private static final Logger log = LoggerFactory.getLogger(FinancialStatementFetchService.class);
  /** 사업보고서 → 사업연도 = (현재 - 1) 또는 (- 2). 6월 말 전이면 작년치만 공시 가능. */
  private static final int[] CANDIDATE_YEAR_DELTAS = {1, 2};

  private final DartFinanceClient dartFinanceClient;
  private final FinancialStatementRepository financialStatementRepository;
  private final StockRepository stockRepository;

  public FinancialStatementFetchService(
      DartFinanceClient dartFinanceClient,
      FinancialStatementRepository financialStatementRepository,
      StockRepository stockRepository) {
    this.dartFinanceClient = dartFinanceClient;
    this.financialStatementRepository = financialStatementRepository;
    this.stockRepository = stockRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void refreshIfMissing(String ticker) {
    Stock stock = stockRepository.findById(ticker).orElse(null);
    if (stock == null || stock.getCorpCode() == null || stock.getCorpCode().isBlank()) return;

    int currentYear = LocalDate.now().getYear();
    for (int delta : CANDIDATE_YEAR_DELTAS) {
      int year = currentYear - delta;
      String period = String.valueOf(year);
      if (financialStatementRepository
          .findByStockTickerAndPeriodTypeAndPeriod(ticker, PeriodType.ANNUAL, period)
          .isPresent()) {
        return; // 이미 적재됨
      }
      List<FinanceRow> rows =
          dartFinanceClient.fetch(stock.getCorpCode(), year, "11011", "CFS");
      if (rows.isEmpty()) {
        // 연결 없으면 별도 시도
        rows = dartFinanceClient.fetch(stock.getCorpCode(), year, "11011", "OFS");
      }
      if (rows.isEmpty()) continue;

      FinancialStatement fs = new FinancialStatement(stock, PeriodType.ANNUAL, period);
      Long revenue = pickAmount(rows, "매출액");
      Long costOfRevenue = pickAmount(rows, "매출원가");
      Long grossProfit = pickAmount(rows, "매출총이익");
      Long operatingProfit = pickAmount(rows, "영업이익");
      Long netIncome = pickAmount(rows, "당기순이익");
      Long totalAssets = pickAmount(rows, "자산총계");
      Long totalLiabilities = pickAmount(rows, "부채총계");
      Long totalEquity = pickAmount(rows, "자본총계");
      fs.applyDartFinance(
          revenue,
          costOfRevenue,
          grossProfit,
          operatingProfit,
          netIncome,
          totalAssets,
          totalLiabilities,
          totalEquity);
      financialStatementRepository.save(fs);
      log.info("Persisted DART finance for {} {} (revenue={}, op={})", ticker, period, revenue, operatingProfit);
      return; // 한 해 적재됐으면 종료
    }
  }

  private static Long pickAmount(List<FinanceRow> rows, String accountKeyword) {
    for (FinanceRow r : rows) {
      String name = r.accountNm() == null ? "" : r.accountNm().trim();
      if (name.equals(accountKeyword) || name.startsWith(accountKeyword)) {
        return parseLong(r.amount());
      }
    }
    return null;
  }

  private static Long parseLong(String value) {
    if (value == null || value.isBlank() || "-".equals(value)) return null;
    try {
      return Long.parseLong(value.replace(",", "").replace(" ", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
