package com.hongik.grad.stocks.application;

import com.hongik.grad.disclosures.client.DartFinanceClient;
import com.hongik.grad.disclosures.client.DartFinanceClient.FinanceRow;
import com.hongik.grad.stocks.client.NaverFinanceMetricsClient;
import com.hongik.grad.stocks.client.NaverFinanceMetricsClient.Metrics;
import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DART fnlttSinglAcntAll 응답에서 손익계산서(CIS·IS)·재무상태표(BS)·현금흐름표(CF)를 다년치 분기·연간으로 받아
 * financial_statements에 한 row(period·sj_div 단위)씩 적재한다. 최신 row가 5건 이상 있으면 skip.
 */
@Service
public class FinancialStatementFetchService {

  private static final Logger log = LoggerFactory.getLogger(FinancialStatementFetchService.class);
  private static final int MIN_ROWS_TO_SKIP_REFRESH = 20;

  /** DART 보고서 코드. */
  private static final String R_Q1 = "11013";

  private static final String R_HY = "11012";
  private static final String R_Q3 = "11014";
  private static final String R_ANNUAL = "11011";

  private final DartFinanceClient dartFinanceClient;
  private final NaverFinanceMetricsClient naverFinanceMetricsClient;
  private final FinancialStatementRepository financialStatementRepository;
  private final StockRepository stockRepository;

  public FinancialStatementFetchService(
      DartFinanceClient dartFinanceClient,
      NaverFinanceMetricsClient naverFinanceMetricsClient,
      FinancialStatementRepository financialStatementRepository,
      StockRepository stockRepository) {
    this.dartFinanceClient = dartFinanceClient;
    this.naverFinanceMetricsClient = naverFinanceMetricsClient;
    this.financialStatementRepository = financialStatementRepository;
    this.stockRepository = stockRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void refreshIfMissing(String ticker) {
    Stock stock = stockRepository.findById(ticker).orElse(null);
    if (stock == null || stock.getCorpCode() == null || stock.getCorpCode().isBlank()) return;

    // disclosed_at 이 없는 V14 이전 행은 세지 않는다. 다시 받으면서 공시일을 채운다.
    long rowCount = financialStatementRepository.countByStockTickerAndDisclosedAtIsNotNull(ticker);
    if (rowCount >= MIN_ROWS_TO_SKIP_REFRESH) return;

    String corp = stock.getCorpCode();
    int currentYear = LocalDate.now().getYear();

    // 연간 사업보고서: 최근 3년
    for (int year : List.of(currentYear - 3, currentYear - 2, currentYear - 1)) {
      fetchAndPersist(stock, corp, year, R_ANNUAL, PeriodType.ANNUAL, String.valueOf(year));
    }

    // 분기 보고서: 작년치 1Q·반기·3Q + 올해 1Q·반기·3Q (있는 만큼)
    fetchAndPersist(
        stock, corp, currentYear - 1, R_Q1, PeriodType.QUARTERLY, (currentYear - 1) + "Q1");
    fetchAndPersist(
        stock, corp, currentYear - 1, R_HY, PeriodType.QUARTERLY, (currentYear - 1) + "H1");
    fetchAndPersist(
        stock, corp, currentYear - 1, R_Q3, PeriodType.QUARTERLY, (currentYear - 1) + "Q3");
    fetchAndPersist(stock, corp, currentYear, R_Q1, PeriodType.QUARTERLY, currentYear + "Q1");
    fetchAndPersist(stock, corp, currentYear, R_HY, PeriodType.QUARTERLY, currentYear + "H1");
    fetchAndPersist(stock, corp, currentYear, R_Q3, PeriodType.QUARTERLY, currentYear + "Q3");

    // PER·PBR·EPS·배당수익률은 네이버 종목 메인 페이지에서 최신값만 가져와 가장 최신 연간 row에 부착.
    try {
      Metrics m = naverFinanceMetricsClient.fetch(ticker);
      if (m.per() != null || m.pbr() != null || m.eps() != null) {
        BigDecimal roe = ratio(financialStatementRepository, ticker, false);
        BigDecimal roa = ratio(financialStatementRepository, ticker, true);
        FinancialStatement latest =
            financialStatementRepository
                .findByStockTickerAndPeriodTypeAndPeriodAndSjDiv(
                    ticker, PeriodType.ANNUAL, String.valueOf(currentYear - 1), "CIS")
                .orElse(null);
        if (latest != null) {
          latest.applyRatios(m.per(), m.pbr(), m.eps(), m.dividendYield(), roe, roa);
        }
      }
    } catch (Exception e) {
      log.debug("Naver finance metrics skipped for {}: {}", ticker, e.getMessage());
    }
  }

  private void fetchAndPersist(
      Stock stock, String corp, int year, String reprtCode, PeriodType pt, String period) {
    String fsDiv = "CFS";
    List<FinanceRow> rows = dartFinanceClient.fetch(corp, year, reprtCode, fsDiv);
    if (rows.isEmpty()) {
      fsDiv = "OFS";
      rows = dartFinanceClient.fetch(corp, year, reprtCode, fsDiv);
    }
    if (rows.isEmpty()) return;
    LocalDate disclosedAt =
        rows.stream()
            .map(r -> parseDisclosedAt(r.rceptNo()))
            .filter(d -> d != null)
            .findFirst()
            .orElse(null);

    Map<String, List<FinanceRow>> byDiv =
        rows.stream()
            .filter(r -> normalizeDiv(r.sjDiv()) != null)
            .collect(Collectors.groupingBy(r -> normalizeDiv(r.sjDiv())));

    for (Map.Entry<String, List<FinanceRow>> entry : byDiv.entrySet()) {
      String sjDiv = entry.getKey();
      FinancialStatement existing =
          financialStatementRepository
              .findByStockTickerAndPeriodTypeAndPeriodAndSjDiv(stock.getTicker(), pt, period, sjDiv)
              .orElse(null);
      if (existing != null) {
        if (existing.getDisclosedAt() == null) existing.assignDisclosedAt(disclosedAt);
        continue;
      }
      FinancialStatement fs = new FinancialStatement(stock, pt, period);
      fs.assignSjDiv(sjDiv);
      fs.assignFsDiv(fsDiv);
      fs.assignDisclosedAt(disclosedAt);
      List<FinanceRow> lines = entry.getValue();

      if ("BS".equals(sjDiv)) {
        fs.applyBalanceSheet(
            pick(lines, "유동자산"),
            pick(lines, "비유동자산"),
            pick(lines, "자산총계"),
            pick(lines, "유동부채"),
            pick(lines, "비유동부채"),
            pick(lines, "부채총계"),
            pick(lines, "자본금"),
            pick(lines, "이익잉여금"),
            pick(lines, "자본총계"));
      } else if ("CF".equals(sjDiv)) {
        // 기말 현금 계정명은 회사마다 "기말의 현금…" / "기말현금…" 으로 갈린다.
        fs.applyCashFlow(
            pick(lines, "영업활동"), pick(lines, "투자활동"), pick(lines, "재무활동"), pick(lines, "기말"));
      } else {
        // CIS / IS — 손익계산서
        fs.applyIncomeStatement(
            pick(lines, "매출액"),
            pick(lines, "매출원가"),
            pick(lines, "매출총이익"),
            pick(lines, "판매비와관리비"),
            pick(lines, "영업이익"),
            pick(lines, "영업외수익"),
            pick(lines, "영업외비용"),
            pick(lines, "법인세비용차감전"),
            pick(lines, "법인세비용"),
            pick(lines, "당기순이익"),
            pickNetIncomeShare(lines, "지배기업"),
            pickNetIncomeShare(lines, "비지배"));
      }
      financialStatementRepository.save(fs);
    }
    log.info(
        "Persisted DART finance for {} {} ({}) → groups={}",
        stock.getTicker(),
        period,
        fsDiv,
        byDiv.keySet());
  }

  /** 접수번호 앞 8 자리(yyyyMMdd)가 접수일이다. 형식이 다르면 null — 그 행은 되감기 계산에서 빠진다. */
  static LocalDate parseDisclosedAt(String rceptNo) {
    if (rceptNo == null || rceptNo.length() < 8) return null;
    try {
      return LocalDate.parse(rceptNo.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** 자본변동표(SCE) 등은 버린다. 손익계산서로 합치면 "지배기업…자본" 행이 순이익 자리에 들어간다. */
  static String normalizeDiv(String div) {
    if (div == null) return "CIS";
    String d = div.trim().toUpperCase();
    return switch (d) {
      case "BS" -> "BS";
      case "CF" -> "CF";
      case "IS", "CIS" -> "CIS";
      default -> null;
    };
  }

  /** 지배/비지배 귀속 순이익. 같은 키워드의 "포괄손익, 지배기업…" 행이 먼저 나오므로 순이익 행만 고른다. */
  static Long pickNetIncomeShare(List<FinanceRow> rows, String keyword) {
    for (FinanceRow r : rows) {
      String name = r.accountNm() == null ? "" : r.accountNm().trim();
      if (name.contains(keyword) && name.contains("순이익") && !name.contains("포괄")) {
        return parseLong(r.amount());
      }
    }
    return null;
  }

  /** 계정명이 정확히 같은 행을 먼저 찾는다. "부채총계" 가 앞선 "자본과부채총계" 행에 걸리지 않게. */
  static Long pick(List<FinanceRow> rows, String keyword) {
    for (FinanceRow r : rows) {
      String name = r.accountNm() == null ? "" : r.accountNm().replace(" ", "");
      if (name.equals(keyword)) return parseLong(r.amount());
    }
    for (FinanceRow r : rows) {
      String name = r.accountNm() == null ? "" : r.accountNm().trim();
      if (name.contains(keyword)) return parseLong(r.amount());
    }
    return null;
  }

  private static Long parseLong(String value) {
    if (value == null || value.isBlank() || "-".equals(value)) return null;
    try {
      return Long.parseLong(value.replace(",", "").replace(" ", "").trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** ROE = netIncome / totalEquity, ROA = netIncome / totalAssets. asRoa=true → ROA. */
  private static BigDecimal ratio(FinancialStatementRepository repo, String ticker, boolean asRoa) {
    return repo.findByStockTickerOrderByPeriodDesc(ticker).stream()
        .filter(f -> f.getPeriodType() == PeriodType.ANNUAL)
        .filter(f -> "CIS".equals(f.getSjDiv()))
        .findFirst()
        .map(
            f -> {
              Long ni = f.getNetIncome();
              // 자산·자본은 같은 기간의 재무상태표(BS) 행에 있다.
              FinancialStatement bs =
                  repo.findByStockTickerAndPeriodTypeAndPeriodAndSjDiv(
                          ticker, PeriodType.ANNUAL, f.getPeriod(), "BS")
                      .orElse(null);
              if (bs == null) return null;
              Long denom = asRoa ? bs.getTotalAssets() : bs.getTotalEquity();
              if (ni == null || denom == null || denom == 0) return null;
              return new BigDecimal(ni)
                  .divide(new BigDecimal(denom), 6, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100))
                  .setScale(2, RoundingMode.HALF_UP);
            })
        .orElse(null);
  }
}
