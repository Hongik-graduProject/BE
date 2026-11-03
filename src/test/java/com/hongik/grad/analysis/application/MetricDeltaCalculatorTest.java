package com.hongik.grad.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricDeltaCalculatorTest {

  @Mock private FinancialStatementRepository repository;
  @InjectMocks private MetricDeltaCalculator calculator;

  private final Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);

  @Test
  @DisplayName("손익계산서 연간 행이 둘 이상이면 연간 대 연간으로 비교한다 (BS 행·분기 누적 행은 쓰지 않음)")
  void prefersAnnualPair() {
    FinancialStatement y2025 = new FinancialStatement(stock, PeriodType.ANNUAL, "2025");
    FinancialStatement y2024 = new FinancialStatement(stock, PeriodType.ANNUAL, "2024");
    FinancialStatement bs2025 = new FinancialStatement(stock, PeriodType.ANNUAL, "2025");
    bs2025.assignSjDiv("BS");
    when(repository.findByStockTickerAndPeriodTypeOrderByPeriodDesc("005930", PeriodType.ANNUAL))
        .thenReturn(List.of(bs2025, y2025, y2024));

    MetricDeltaCalculator.DeltaSnapshot snapshot = calculator.computeForTicker("005930");

    assertThat(snapshot.latest()).isSameAs(y2025);
    assertThat(snapshot.previous()).isSameAs(y2024);
  }

  @Test
  @DisplayName("연간 행이 하나 이하면 시간 순 최신 분기를 전년 같은 분기와 비교한다 (누적치끼리 섞지 않음)")
  void fallsBackToSameQuarterLastYear() {
    // 문자열 내림차순으로는 2026Q1 이 2026H1 보다 앞이지만 시간 순 최신은 2026H1 이다.
    FinancialStatement q1 = new FinancialStatement(stock, PeriodType.QUARTERLY, "2026Q1");
    FinancialStatement h1 = new FinancialStatement(stock, PeriodType.QUARTERLY, "2026H1");
    FinancialStatement lastH1 = new FinancialStatement(stock, PeriodType.QUARTERLY, "2025H1");
    when(repository.findByStockTickerAndPeriodTypeOrderByPeriodDesc("005930", PeriodType.ANNUAL))
        .thenReturn(List.of());
    when(repository.findByStockTickerOrderByPeriodDesc("005930"))
        .thenReturn(List.of(q1, h1, lastH1));

    MetricDeltaCalculator.DeltaSnapshot snapshot = calculator.computeForTicker("005930");

    assertThat(snapshot.latest()).isSameAs(h1);
    assertThat(snapshot.previous()).isSameAs(lastH1);
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
}
