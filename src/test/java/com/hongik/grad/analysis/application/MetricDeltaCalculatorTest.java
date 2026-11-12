package com.hongik.grad.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricDeltaCalculatorTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 11, 1);

  @Mock private FinancialStatementRepository repository;
  @InjectMocks private MetricDeltaCalculator calculator;

  private final Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);

  @Test
  @DisplayName("손익계산서 연간 행이 둘 이상이면 연간 대 연간으로 비교한다 (BS 행·분기 누적 행은 쓰지 않음)")
  void prefersAnnualPair() {
    FinancialStatement y2025 = new FinancialStatement(stock, PeriodType.ANNUAL, "2025");
    FinancialStatement y2024 = new FinancialStatement(stock, PeriodType.ANNUAL, "2024");
    FinancialStatement bs2025 = row(PeriodType.ANNUAL, "2025", "BS");
    FinancialStatement q3 = new FinancialStatement(stock, PeriodType.QUARTERLY, "2026Q3");
    stub(List.of(q3, bs2025, y2025, y2024));

    MetricDeltaCalculator.DeltaSnapshot snapshot = calculator.computeForTicker("005930", AS_OF);

    assertThat(snapshot.latest()).isSameAs(y2025);
    assertThat(snapshot.previous()).isSameAs(y2024);
    assertThat(snapshot.latestBs()).isSameAs(bs2025);
    assertThat(snapshot.previousBs()).isNull();
  }

  @Test
  @DisplayName("연간 행이 하나 이하면 시간 순 최신 분기를 전년 같은 분기와 비교한다 (누적치끼리 섞지 않음)")
  void fallsBackToSameQuarterLastYear() {
    // 문자열 내림차순으로는 2026Q1 이 2026H1 보다 앞이지만 시간 순 최신은 2026H1 이다.
    FinancialStatement q1 = new FinancialStatement(stock, PeriodType.QUARTERLY, "2026Q1");
    FinancialStatement h1 = new FinancialStatement(stock, PeriodType.QUARTERLY, "2026H1");
    FinancialStatement lastH1 = new FinancialStatement(stock, PeriodType.QUARTERLY, "2025H1");
    stub(List.of(q1, h1, lastH1));

    MetricDeltaCalculator.DeltaSnapshot snapshot = calculator.computeForTicker("005930", AS_OF);

    assertThat(snapshot.latest()).isSameAs(h1);
    assertThat(snapshot.previous()).isSameAs(lastH1);
  }

  @Test
  @DisplayName("ROE·부채비율은 같은 기간 BS 행의 자본·부채로 계산한다 (CIS 행에는 자본이 없다)")
  void readsBalanceSheetMetricsFromBsRow() {
    // 실제 적재 형태: 기간마다 CIS 행(손익)과 BS 행(재무상태)이 따로 있다.
    FinancialStatement cis2025 = row(PeriodType.ANNUAL, "2025", "CIS");
    cis2025.applyIncomeStatement(
        1000L, null, null, null, 100L, null, null, null, null, 100L, null, null);
    FinancialStatement cis2024 = row(PeriodType.ANNUAL, "2024", "CIS");
    cis2024.applyIncomeStatement(
        1000L, null, null, null, 50L, null, null, null, null, 50L, null, null);
    FinancialStatement bs2025 = row(PeriodType.ANNUAL, "2025", "BS");
    bs2025.applyBalanceSheet(null, null, 2000L, null, null, 500L, null, null, 1000L);
    FinancialStatement bs2024 = row(PeriodType.ANNUAL, "2024", "BS");
    bs2024.applyBalanceSheet(null, null, 2000L, null, null, 1000L, null, null, 1000L);
    stub(List.of(bs2025, cis2025, bs2024, cis2024));

    MetricDeltaCalculator.DeltaSnapshot snapshot = calculator.computeForTicker("005930", AS_OF);

    // ROE 10% → 5%: +5%p. 부채비율 50% → 100%: -50%p 이지만 ±20%p 로 잘린다.
    assertThat(calculator.delta(snapshot, "roe")).contains(5.0);
    assertThat(calculator.delta(snapshot, "debt_ratio")).contains(-20.0);
    assertThat(calculator.delta(snapshot, "roa")).contains(2.5);
  }

  @Test
  @DisplayName("asOf 이전에 공시된 행만 조회한다 (look-ahead bias 차단)")
  void queriesOnlyRowsDisclosedByAsOf() {
    stub(List.of());

    assertThat(calculator.computeForTicker("005930", AS_OF).latest()).isNull();
    verify(repository)
        .findByStockTickerAndDisclosedAtLessThanEqualOrderByPeriodDesc("005930", AS_OF);
  }

  @Test
  @DisplayName("극단값은 비율 ±20%p, 성장률 ±50% 로 자른다")
  void clampsExtremeDeltas() {
    assertThat(MetricDeltaCalculator.clamp(418.0, MetricCanonicalCode.ComputeStrategy.RAW_DIFF))
        .isEqualTo(20.0);
    assertThat(MetricDeltaCalculator.clamp(-80.0, MetricCanonicalCode.ComputeStrategy.YOY_PERCENT))
        .isEqualTo(-50.0);
    assertThat(MetricDeltaCalculator.clamp(2.19, MetricCanonicalCode.ComputeStrategy.RAW_DIFF))
        .isEqualTo(2.19);
  }

  private void stub(List<FinancialStatement> rows) {
    when(repository.findByStockTickerAndDisclosedAtLessThanEqualOrderByPeriodDesc("005930", AS_OF))
        .thenReturn(rows);
  }

  private FinancialStatement row(PeriodType type, String period, String sjDiv) {
    FinancialStatement fs = new FinancialStatement(stock, type, period);
    fs.assignSjDiv(sjDiv);
    return fs;
  }
}
