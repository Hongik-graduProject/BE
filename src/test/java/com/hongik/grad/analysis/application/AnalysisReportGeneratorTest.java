package com.hongik.grad.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.analysis.event.ReportCreatedEvent;
import com.hongik.grad.analysis.routing.RoutingResult;
import com.hongik.grad.analysis.routing.ThemeRouter;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.themes.domain.Direction;
import com.hongik.grad.themes.domain.FinancialMetricCatalog;
import com.hongik.grad.themes.domain.FinancialMetricCatalogRepository;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeMetricSensitivity;
import com.hongik.grad.themes.domain.ThemeMetricSensitivityRepository;
import com.hongik.grad.themes.domain.ThemeRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AnalysisReportGeneratorTest {

  @Mock private ThemeRouter themeRouter;
  @Mock private ThemeRepository themeRepository;
  @Mock private ThemeMetricSensitivityRepository sensitivityRepository;
  @Mock private FinancialMetricCatalogRepository metricCatalogRepository;
  @Mock private MetricDeltaCalculator metricDeltaCalculator;
  @Mock private AnalysisReportRepository analysisReportRepository;
  @Mock private DisclosureRepository disclosureRepository;
  @Mock private FinancialStatementRepository financialStatementRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private AnalysisReportGenerator generator;

  @BeforeEach
  void setUp() {
    generator =
        new AnalysisReportGenerator(
            themeRouter,
            themeRepository,
            sensitivityRepository,
            metricCatalogRepository,
            metricDeltaCalculator,
            analysisReportRepository,
            disclosureRepository,
            financialStatementRepository,
            eventPublisher,
            "themesense-stage1-v1.0",
            "themesense-stage2-v1.0");
  }

  @Test
  @DisplayName("Disclosure → applied_theme, expected_car, metric_contributions[5] 채워진 보고서 INSERT")
  void generate_persistsReport() throws Exception {
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    Theme theme = newTheme(1L, "AI/반도체");
    Disclosure disclosure = newDisclosure(100L, stock);

    when(themeRouter.route(disclosure))
        .thenReturn(Optional.of(new RoutingResult(1L, "AI/반도체", 0.9234)));
    when(analysisReportRepository.findByStockTickerAndDisclosureId("005930", 100L))
        .thenReturn(Optional.empty());
    when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));

    List<ThemeMetricSensitivity> sensitivities =
        List.of(
            newSensitivity(theme, "per", 0.35, 0.35, 1, Direction.POSITIVE),
            newSensitivity(theme, "roe", -0.28, 0.28, 2, Direction.NEGATIVE),
            newSensitivity(theme, "op_margin", 0.22, 0.22, 3, Direction.POSITIVE),
            newSensitivity(theme, "debt_ratio", -0.18, 0.18, 4, Direction.NEGATIVE),
            newSensitivity(theme, "net_margin", 0.12, 0.12, 5, Direction.POSITIVE));
    when(sensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(
            1L, "themesense-stage2-v1.0"))
        .thenReturn(sensitivities);

    FinancialStatement latest = new FinancialStatement(stock, PeriodType.QUARTERLY, "2024Q4");
    when(metricDeltaCalculator.computeForTicker("005930"))
        .thenReturn(new MetricDeltaCalculator.DeltaSnapshot(latest, null));
    when(metricDeltaCalculator.delta(any(), any())).thenAnswer(invocation -> Optional.of(1.0));

    when(metricCatalogRepository.findById(any()))
        .thenAnswer(
            inv -> {
              FinancialMetricCatalog c =
                  new FinancialMetricCatalog(inv.getArgument(0), "metric", "metric");
              return Optional.of(c);
            });

    when(analysisReportRepository.save(any(AnalysisReport.class)))
        .thenAnswer(
            inv -> {
              AnalysisReport ar = inv.getArgument(0);
              setField(ar, "id", 42L);
              setField(ar, "generatedAt", OffsetDateTime.now());
              return ar;
            });

    Optional<AnalysisReport> result = generator.generate(disclosure);

    assertThat(result).isPresent();
    AnalysisReport saved = result.get();
    assertThat(saved.getStock().getTicker()).isEqualTo("005930");
    assertThat(saved.getAppliedTheme().getId()).isEqualTo(1L);
    assertThat(saved.getMetricContributions()).hasSize(5);
    // expected_car = Σ(β × Δ) = 0.35 + (-0.28) + 0.22 + (-0.18) + 0.12 = 0.23
    assertThat(saved.getExpectedCar()).isEqualByComparingTo(new BigDecimal("0.2300"));
    assertThat(saved.getCarLower()).isEqualByComparingTo(new BigDecimal("-1.7700"));
    assertThat(saved.getCarUpper()).isEqualByComparingTo(new BigDecimal("2.2300"));
    assertThat(saved.getThemeRoutingScore()).isEqualByComparingTo(new BigDecimal("0.9234"));
    assertThat(saved.getStage1ModelVersion()).isEqualTo("themesense-stage1-v1.0");
    assertThat(saved.getStage2ModelVersion()).isEqualTo("themesense-stage2-v1.0");

    // M8: sorted by |contribution| desc — all delta=1.0 so |contribution|=|beta|
    List<MetricContribution> contributions = saved.getMetricContributions();
    assertThat(Math.abs(contributions.get(0).contribution()))
        .isGreaterThanOrEqualTo(Math.abs(contributions.get(1).contribution()));

    ArgumentCaptor<ReportCreatedEvent> captor = ArgumentCaptor.forClass(ReportCreatedEvent.class);
    verify(eventPublisher, times(1)).publishEvent(captor.capture());
    assertThat(captor.getValue().reportId()).isEqualTo(42L);
    assertThat(captor.getValue().ticker()).isEqualTo("005930");
    assertThat(captor.getValue().appliedThemeId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("학습 universe 외 ticker는 보고서 미생성 + 이벤트 미발행")
  void generate_skipsForUnknownTicker() throws Exception {
    Stock stock = new Stock("999999", "Unknown", Market.KOSPI);
    Disclosure disclosure = newDisclosure(101L, stock);
    when(themeRouter.route(disclosure)).thenReturn(Optional.empty());

    Optional<AnalysisReport> result = generator.generate(disclosure);

    assertThat(result).isEmpty();
    verify(analysisReportRepository, times(0)).save(any());
    verify(eventPublisher, times(0)).publishEvent(any());
  }

  @Test
  @DisplayName("sensitivities 비어있으면 contributions 비어있고 보고서는 생성됨 (warn 로그)")
  void generate_emptySensitivities_reportWithNoContributions() throws Exception {
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    Theme theme = newTheme(1L, "AI/반도체");
    Disclosure disclosure = newDisclosure(200L, stock);

    when(themeRouter.route(disclosure))
        .thenReturn(Optional.of(new RoutingResult(1L, "AI/반도체", 0.9)));
    when(analysisReportRepository.findByStockTickerAndDisclosureId("005930", 200L))
        .thenReturn(Optional.empty());
    when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));
    when(sensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(
            1L, "themesense-stage2-v1.0"))
        .thenReturn(List.of());

    FinancialStatement latest = new FinancialStatement(stock, PeriodType.QUARTERLY, "2024Q4");
    when(metricDeltaCalculator.computeForTicker("005930"))
        .thenReturn(new MetricDeltaCalculator.DeltaSnapshot(latest, null));

    when(analysisReportRepository.save(any(AnalysisReport.class)))
        .thenAnswer(
            inv -> {
              AnalysisReport ar = inv.getArgument(0);
              setField(ar, "id", 99L);
              setField(ar, "generatedAt", OffsetDateTime.now());
              return ar;
            });

    Optional<AnalysisReport> result = generator.generate(disclosure);
    assertThat(result).isPresent();
    assertThat(result.get().getMetricContributions()).isEmpty();
    assertThat(result.get().getExpectedCar()).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));
  }

  @Test
  @DisplayName("PER/EPS_GR/REV_GR/DER alias들이 delta 계산에 올바르게 매핑됨")
  void generate_uppercaseAliases_mappedCorrectly() throws Exception {
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    Theme theme = newTheme(1L, "AI/반도체");
    Disclosure disclosure = newDisclosure(300L, stock);

    when(themeRouter.route(disclosure))
        .thenReturn(Optional.of(new RoutingResult(1L, "AI/반도체", 0.85)));
    when(analysisReportRepository.findByStockTickerAndDisclosureId("005930", 300L))
        .thenReturn(Optional.empty());
    when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));

    // ETL stores UPPERCASE codes as in the fixture
    List<ThemeMetricSensitivity> sensitivities =
        List.of(
            newSensitivity(theme, "PER", 0.35, 0.35, 1, Direction.POSITIVE),
            newSensitivity(theme, "EPS_GR", 0.22, 0.22, 2, Direction.POSITIVE),
            newSensitivity(theme, "REV_GR", 0.42, 0.42, 3, Direction.POSITIVE),
            newSensitivity(theme, "DER", -0.31, 0.31, 4, Direction.NEGATIVE));
    when(sensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(
            1L, "themesense-stage2-v1.0"))
        .thenReturn(sensitivities);

    FinancialStatement latest = new FinancialStatement(stock, PeriodType.QUARTERLY, "2024Q4");
    when(metricDeltaCalculator.computeForTicker("005930"))
        .thenReturn(new MetricDeltaCalculator.DeltaSnapshot(latest, null));
    // Each alias must resolve and return a non-empty delta
    when(metricDeltaCalculator.delta(any(), any())).thenAnswer(inv -> Optional.of(2.0));

    when(metricCatalogRepository.findById(any()))
        .thenAnswer(
            inv -> Optional.of(new FinancialMetricCatalog(inv.getArgument(0), "테스트지표", "Test")));

    when(analysisReportRepository.save(any(AnalysisReport.class)))
        .thenAnswer(
            inv -> {
              AnalysisReport ar = inv.getArgument(0);
              setField(ar, "id", 77L);
              setField(ar, "generatedAt", OffsetDateTime.now());
              return ar;
            });

    Optional<AnalysisReport> result = generator.generate(disclosure);
    assertThat(result).isPresent();
    // expected_car = (0.35+0.22+0.42-0.31) * 2.0 = 0.68 * 2.0 = 1.36
    assertThat(result.get().getExpectedCar()).isEqualByComparingTo(new BigDecimal("1.3600"));
    // All 4 sensitivities contributed (delta was non-empty for all)
    assertThat(result.get().getMetricContributions()).hasSize(4);
  }

  private Theme newTheme(Long id, String name) throws Exception {
    Theme t = new Theme(name);
    setField(t, "id", id);
    return t;
  }

  private Disclosure newDisclosure(Long id, Stock stock) throws Exception {
    Disclosure d =
        new Disclosure(
            stock, "20240101000001", "사업보고서", DisclosureType.REPORT, OffsetDateTime.now());
    setField(d, "id", id);
    return d;
  }

  private ThemeMetricSensitivity newSensitivity(
      Theme theme, String code, double beta, double absBeta, int rank, Direction direction) {
    ThemeMetricSensitivity s = new ThemeMetricSensitivity(theme, code, beta, absBeta, rank);
    s.setDirection(direction);
    return s;
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Class<?> clazz = target.getClass();
    while (clazz != null) {
      try {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException ignored) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
