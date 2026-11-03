package com.hongik.grad.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.application.FinancialStatementFetchService;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReportBackfillServiceTest {

  @Mock private StockThemeRepository stockThemeRepository;
  @Mock private DisclosureRepository disclosureRepository;
  @Mock private AnalysisReportRepository analysisReportRepository;
  @Mock private FinancialStatementFetchService financialStatementFetchService;
  @Mock private AnalysisReportGenerator analysisReportGenerator;

  private ReportBackfillService service;
  private final Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);

  @BeforeEach
  void setUp() {
    service =
        new ReportBackfillService(
            stockThemeRepository,
            disclosureRepository,
            analysisReportRepository,
            financialStatementFetchService,
            analysisReportGenerator);
  }

  @Test
  @DisplayName("테마가 없는 종목은 아무것도 하지 않는다")
  void noTheme_noop() {
    when(stockThemeRepository.findByStockTickerOrderByRankAsc("005930")).thenReturn(List.of());

    service.ensureRecentReports("005930");

    verify(disclosureRepository, never()).findByStockTickerOrderByFiledAtDesc(anyString(), any());
    verify(financialStatementFetchService, never()).refreshIfMissing(anyString());
  }

  @Test
  @DisplayName("보고서가 없는 최근 공시만 재무를 받은 뒤 생성한다")
  void missingReports_fetchFinanceThenGenerate() throws Exception {
    when(stockThemeRepository.findByStockTickerOrderByRankAsc("005930"))
        .thenReturn(List.of(new StockTheme(stock, new Theme("AI/반도체"), (short) 1)));
    when(disclosureRepository.findByStockTickerOrderByFiledAtDesc(
            any(String.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(disclosure(1L), disclosure(2L))));
    when(analysisReportRepository.findByStockTickerAndDisclosureId("005930", 1L))
        .thenReturn(Optional.of(new AnalysisReport(stock, new Theme("AI/반도체"), "t")));
    when(analysisReportRepository.findByStockTickerAndDisclosureId("005930", 2L))
        .thenReturn(Optional.empty());

    when(analysisReportGenerator.generate(2L))
        .thenReturn(Optional.of(new AnalysisReport(stock, new Theme("AI/반도체"), "t")));

    assertThat(service.ensureRecentReports("005930")).isTrue();

    verify(financialStatementFetchService).refreshIfMissing("005930");
    verify(analysisReportGenerator).generate(2L);
    verify(analysisReportGenerator, never()).generate(1L);
  }

  @Test
  @DisplayName("모든 최근 공시에 보고서가 있으면 재무를 다시 받지 않는다")
  void allPresent_noFetch() throws Exception {
    when(stockThemeRepository.findByStockTickerOrderByRankAsc("005930"))
        .thenReturn(List.of(new StockTheme(stock, new Theme("AI/반도체"), (short) 1)));
    when(disclosureRepository.findByStockTickerOrderByFiledAtDesc(
            any(String.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(disclosure(1L))));
    when(analysisReportRepository.findByStockTickerAndDisclosureId("005930", 1L))
        .thenReturn(Optional.of(new AnalysisReport(stock, new Theme("AI/반도체"), "t")));

    service.ensureRecentReports("005930");

    verify(financialStatementFetchService, never()).refreshIfMissing(anyString());
    verify(analysisReportGenerator, never()).generate(anyLong());
  }

  private Disclosure disclosure(Long id) throws Exception {
    Disclosure d =
        new Disclosure(
            stock, "2026110100000" + id, "사업보고서", DisclosureType.REPORT, OffsetDateTime.now());
    Field f = Disclosure.class.getDeclaredField("id");
    f.setAccessible(true);
    f.set(d, id);
    return d;
  }
}
