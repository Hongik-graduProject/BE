package com.hongik.grad.stocks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.disclosures.client.DartFinanceClient;
import com.hongik.grad.disclosures.client.DartFinanceClient.FinanceRow;
import com.hongik.grad.stocks.client.NaverFinanceMetricsClient;
import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialStatementFetchServiceTest {

  @Mock private DartFinanceClient dartFinanceClient;
  @Mock private NaverFinanceMetricsClient naverFinanceMetricsClient;
  @Mock private FinancialStatementRepository repository;
  @Mock private StockRepository stockRepository;
  @InjectMocks private FinancialStatementFetchService service;

  @Test
  @DisplayName("새 행에는 rcept_no 공시일을 넣고, 공시일이 비어 있는 기존 행은 채운다")
  void storesAndBackfillsDisclosedAt() {
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    stock.assignCorpCode("00126380");
    when(stockRepository.findById("005930")).thenReturn(Optional.of(stock));
    when(repository.countByStockTickerAndDisclosedAtIsNotNull("005930")).thenReturn(0L);
    String lastYear = String.valueOf(LocalDate.now().getYear() - 1);
    lenient()
        .when(dartFinanceClient.fetch(anyString(), anyInt(), anyString(), anyString()))
        .thenReturn(List.of());
    when(dartFinanceClient.fetch("00126380", LocalDate.now().getYear() - 1, "11011", "CFS"))
        .thenReturn(
            List.of(
                new FinanceRow("매출액", "1,000", "CIS", "20250311000456"),
                new FinanceRow("자산총계", "5,000", "BS", "20250311000456")));
    FinancialStatement oldBs = new FinancialStatement(stock, PeriodType.ANNUAL, lastYear);
    oldBs.assignSjDiv("BS");
    lenient()
        .when(
            repository.findByStockTickerAndPeriodTypeAndPeriodAndSjDiv(
                anyString(), any(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(repository.findByStockTickerAndPeriodTypeAndPeriodAndSjDiv(
            "005930", PeriodType.ANNUAL, lastYear, "BS"))
        .thenReturn(Optional.of(oldBs));
    when(naverFinanceMetricsClient.fetch("005930"))
        .thenReturn(NaverFinanceMetricsClient.Metrics.empty());

    service.refreshIfMissing("005930");

    ArgumentCaptor<FinancialStatement> saved = ArgumentCaptor.forClass(FinancialStatement.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getSjDiv()).isEqualTo("CIS");
    assertThat(saved.getValue().getDisclosedAt()).isEqualTo(LocalDate.of(2025, 3, 11));
    assertThat(oldBs.getDisclosedAt()).isEqualTo(LocalDate.of(2025, 3, 11));
    verify(repository).countByStockTickerAndDisclosedAtIsNotNull(eq("005930"));
  }
}
