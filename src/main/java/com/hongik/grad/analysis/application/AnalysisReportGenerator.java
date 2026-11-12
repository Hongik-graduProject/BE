package com.hongik.grad.analysis.application;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.analysis.dto.ReportMetrics;
import com.hongik.grad.analysis.event.ReportCreatedEvent;
import com.hongik.grad.analysis.routing.RoutingResult;
import com.hongik.grad.analysis.routing.ThemeRouter;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.themes.domain.FinancialMetricCatalog;
import com.hongik.grad.themes.domain.FinancialMetricCatalogRepository;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeMetricSensitivity;
import com.hongik.grad.themes.domain.ThemeMetricSensitivityRepository;
import com.hongik.grad.themes.domain.ThemeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces an {@link AnalysisReport} for a single disclosure identified by its id. Accepts a
 * disclosureId (Long) rather than a Disclosure entity to avoid LazyInitializationException when
 * called from an {@code @Async} / {@code AFTER_COMMIT} listener context (M4).
 *
 * <p>v1.0 limitation: contribution uses raw metric delta (no z-score normalization);
 * car_lower/upper are placeholders (expected_car ± 2).
 */
@Service
public class AnalysisReportGenerator {

  private static final Logger log = LoggerFactory.getLogger(AnalysisReportGenerator.class);
  private static final int TOP_SENSITIVE_METRICS = 5;
  private static final BigDecimal CAR_BAND = new BigDecimal("2.0000");
  private static final int CONTRIB_SCALE = 6;
  private static final int CAR_SCALE = 4;
  private static final int ROUTING_SCORE_SCALE = 4;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ThemeRouter themeRouter;
  private final ThemeRepository themeRepository;
  private final ThemeMetricSensitivityRepository sensitivityRepository;
  private final FinancialMetricCatalogRepository metricCatalogRepository;
  private final MetricDeltaCalculator metricDeltaCalculator;
  private final AnalysisReportRepository analysisReportRepository;
  private final DisclosureRepository disclosureRepository;
  private final FinancialStatementRepository financialStatementRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final String stage1Version;
  private final String stage2Version;

  public AnalysisReportGenerator(
      ThemeRouter themeRouter,
      ThemeRepository themeRepository,
      ThemeMetricSensitivityRepository sensitivityRepository,
      FinancialMetricCatalogRepository metricCatalogRepository,
      MetricDeltaCalculator metricDeltaCalculator,
      AnalysisReportRepository analysisReportRepository,
      DisclosureRepository disclosureRepository,
      FinancialStatementRepository financialStatementRepository,
      ApplicationEventPublisher eventPublisher,
      @Value("${themesense.stage1-version:themesense-stage1-v1.0}") String stage1Version,
      @Value("${themesense.stage2-version:themesense-stage2-v1.0}") String stage2Version) {
    this.themeRouter = themeRouter;
    this.themeRepository = themeRepository;
    this.sensitivityRepository = sensitivityRepository;
    this.metricCatalogRepository = metricCatalogRepository;
    this.metricDeltaCalculator = metricDeltaCalculator;
    this.analysisReportRepository = analysisReportRepository;
    this.disclosureRepository = disclosureRepository;
    this.financialStatementRepository = financialStatementRepository;
    this.eventPublisher = eventPublisher;
    this.stage1Version = stage1Version;
    this.stage2Version = stage2Version;
  }

  /**
   * Entry point for the listener. Accepts a disclosureId so that the LAZY-loaded Disclosure
   * associations are fetched fresh inside this REQUIRES_NEW transaction (M4).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<AnalysisReport> generate(Long disclosureId) {
    Disclosure disclosure =
        disclosureRepository
            .findById(disclosureId)
            .orElseThrow(
                () ->
                    new IllegalStateException("Disclosure missing after commit: " + disclosureId));
    return generateFromDisclosure(disclosure);
  }

  /**
   * Overload accepting a Disclosure object directly (used by unit tests that pass a fully-loaded
   * entity). Falls through to the same generation logic.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<AnalysisReport> generate(Disclosure disclosure) {
    return generateFromDisclosure(disclosure);
  }

  private Optional<AnalysisReport> generateFromDisclosure(Disclosure disclosure) {
    String ticker = disclosure.getStock().getTicker();

    Optional<RoutingResult> routed = themeRouter.route(disclosure);
    if (routed.isEmpty()) {
      log.warn("Skip report — ticker outside learning universe: {}", ticker);
      return Optional.empty();
    }
    RoutingResult routing = routed.get();

    Optional<AnalysisReport> existing =
        analysisReportRepository.findByStockTickerAndDisclosureId(ticker, disclosure.getId());
    if (existing.isPresent()) {
      log.info(
          "Skip report — already exists for ticker={} disclosure_id={}",
          ticker,
          disclosure.getId());
      return existing;
    }

    Theme appliedTheme =
        themeRepository
            .findById(routing.themeId())
            .orElseThrow(
                () -> new IllegalStateException("applied_theme not found: " + routing.themeId()));

    List<ThemeMetricSensitivity> sensitivities =
        sensitivityRepository
            .findByThemeIdAndModelVersionOrderByRankAsc(routing.themeId(), stage2Version)
            .stream()
            .limit(TOP_SENSITIVE_METRICS)
            .toList();

    if (sensitivities.isEmpty()) {
      log.warn(
          "No sensitivities found for theme_id={} version={} ticker={} — contributions will be"
              + " empty",
          routing.themeId(),
          stage2Version,
          ticker);
    }

    // 공시 당일까지 공개된 재무만 쓴다. 공시 뒤에 나온 실적을 쓰면 되감기 성적이 부풀려진다.
    LocalDate asOf = disclosure.getFiledAt().atZoneSameInstant(KST).toLocalDate();
    MetricDeltaCalculator.DeltaSnapshot snapshot =
        metricDeltaCalculator.computeForTicker(ticker, asOf);
    if (snapshot.latest() == null) {
      // 재무 데이터 없이 만들면 기여도가 전부 0 인 보고서가 남는다. ReportBackfillService 가 재무를 받은 뒤 다시 만든다.
      log.info("Skip report — no financial statements yet for ticker={}", ticker);
      return Optional.empty();
    }

    List<MetricContribution> contributions = new ArrayList<>();
    BigDecimal expectedCar = BigDecimal.ZERO;
    for (ThemeMetricSensitivity s : sensitivities) {
      Optional<Double> deltaOpt = metricDeltaCalculator.delta(snapshot, s.getMetricCode());
      double delta = deltaOpt.orElse(0.0);
      if (deltaOpt.isEmpty()) {
        log.debug(
            "metric_code={} not recognized or no data — contribution=0 for ticker={}",
            s.getMetricCode(),
            ticker);
      }
      double contribution = s.getBeta() * delta;
      expectedCar =
          expectedCar.add(
              BigDecimal.valueOf(contribution).setScale(CONTRIB_SCALE, RoundingMode.HALF_UP));

      // Resolve canonical catalog key for display_name and raw_value
      Optional<MetricCanonicalCode> canonicalOpt =
          MetricCanonicalCode.fromAlias(s.getMetricCode().toLowerCase(Locale.ROOT));
      String catalogKey =
          canonicalOpt
              .map(MetricCanonicalCode::catalogKey)
              .orElse(s.getMetricCode().toLowerCase(Locale.ROOT));
      String displayName = resolveDisplayName(catalogKey, s.getMetricCode());
      String rawValue = buildRawValue(canonicalOpt, snapshot);

      contributions.add(
          new MetricContribution(
              s.getMetricCode(),
              displayName,
              s.getBeta(),
              rawValue,
              delta,
              round(contribution, CONTRIB_SCALE)));
    }

    // M8 — sort by |contribution| descending
    contributions.sort(Comparator.comparingDouble(c -> -Math.abs(c.contribution())));

    BigDecimal expectedCarScaled = expectedCar.setScale(CAR_SCALE, RoundingMode.HALF_UP);
    BigDecimal carLower = expectedCarScaled.subtract(CAR_BAND);
    BigDecimal carUpper = expectedCarScaled.add(CAR_BAND);

    AnalysisReport report =
        new AnalysisReport(disclosure.getStock(), appliedTheme, disclosure.getTitle());
    report.setDisclosure(disclosure);
    report.setThemeRoutingScore(
        BigDecimal.valueOf(routing.score()).setScale(ROUTING_SCORE_SCALE, RoundingMode.HALF_UP));
    report.setSummary(buildSummary(routing, contributions));
    report.setExpectedCar(expectedCarScaled);
    report.setCarLower(carLower);
    report.setCarUpper(carUpper);
    report.setMetricContributions(contributions);
    report.setKeyFindings(List.of());
    report.setMetrics(buildMetrics(snapshot));
    report.setStage1ModelVersion(stage1Version);
    report.setStage2ModelVersion(stage2Version);

    AnalysisReport saved = analysisReportRepository.save(report);
    eventPublisher.publishEvent(
        new ReportCreatedEvent(
            saved.getId(), ticker, appliedTheme.getId(), saved.getTitle(), saved.getGeneratedAt()));
    log.info(
        "Generated analysis_report id={} ticker={} theme={} expected_car={}",
        saved.getId(),
        ticker,
        appliedTheme.getName(),
        expectedCarScaled);
    return Optional.of(saved);
  }

  /** Resolves the catalog display_name (nameKo) using canonical catalogKey. Falls back to code. */
  private String resolveDisplayName(String catalogKey, String fallback) {
    return metricCatalogRepository
        .findById(catalogKey)
        .map(FinancialMetricCatalog::getNameKo)
        .orElse(fallback);
  }

  /**
   * Builds a raw_value string for ratio metrics. v1.0 best-effort: ratio metrics get "x.xx%" format
   * from the latest FinancialStatement. Returns null for metrics without available data.
   */
  private String buildRawValue(
      Optional<MetricCanonicalCode> canonicalOpt, MetricDeltaCalculator.DeltaSnapshot snapshot) {
    if (canonicalOpt.isEmpty() || snapshot.latest() == null) {
      return null;
    }
    MetricCanonicalCode canonical = canonicalOpt.get();
    FinancialStatement fs = snapshot.latest();
    return switch (canonical) {
      case PER ->
          fs.getPer() != null
              ? String.format(Locale.ROOT, "%.2f", fs.getPer().doubleValue())
              : null;
      case PBR ->
          fs.getPbr() != null
              ? String.format(Locale.ROOT, "%.2f", fs.getPbr().doubleValue())
              : null;
      case ROE, OP_MARGIN, NET_MARGIN, DEBT_RATIO, DIVIDEND_YIELD, FOREIGN_RATIO ->
          buildRatioPercent(canonical, fs, snapshot.latestBs());
      default -> null;
    };
  }

  /** 자본·부채는 같은 기간의 재무상태표(BS) 행 {@code bs} 에 있다. */
  private String buildRatioPercent(
      MetricCanonicalCode canonical, FinancialStatement fs, FinancialStatement bs) {
    Long totalEquity = bs == null ? null : bs.getTotalEquity();
    Long totalLiabilities = bs == null ? null : bs.getTotalLiabilities();
    Optional<BigDecimal> val =
        switch (canonical) {
          case ROE -> ratioOpt(fs.getNetIncome(), totalEquity);
          case OP_MARGIN -> ratioOpt(fs.getOperatingProfit(), fs.getRevenue());
          case NET_MARGIN -> ratioOpt(fs.getNetIncome(), fs.getRevenue());
          case DEBT_RATIO -> ratioOpt(totalLiabilities, totalEquity);
          case DIVIDEND_YIELD -> Optional.ofNullable(fs.getDividendYield());
          default -> Optional.empty();
        };
    return val.map(v -> String.format(Locale.ROOT, "%.2f%%", v.doubleValue())).orElse(null);
  }

  private Optional<BigDecimal> ratioOpt(Long numerator, Long denominator) {
    if (numerator == null || denominator == null || denominator == 0L) {
      return Optional.empty();
    }
    return Optional.of(
        BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)));
  }

  private ReportMetrics buildMetrics(MetricDeltaCalculator.DeltaSnapshot snapshot) {
    FinancialStatement latest = snapshot.latest();
    FinancialStatement previous = snapshot.previous();
    if (latest == null) {
      return new ReportMetrics(null, null, null, null, null);
    }
    Double yoyRevGrowth = null;
    Double yoyOpGrowth = null;
    if (previous != null
        && previous.getRevenue() != null
        && previous.getRevenue() != 0L
        && latest.getRevenue() != null) {
      yoyRevGrowth =
          BigDecimal.valueOf(latest.getRevenue() - previous.getRevenue())
              .divide(BigDecimal.valueOf(Math.abs(previous.getRevenue())), 6, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .doubleValue();
    }
    if (previous != null
        && previous.getOperatingProfit() != null
        && previous.getOperatingProfit() != 0L
        && latest.getOperatingProfit() != null) {
      yoyOpGrowth =
          BigDecimal.valueOf(latest.getOperatingProfit() - previous.getOperatingProfit())
              .divide(
                  BigDecimal.valueOf(Math.abs(previous.getOperatingProfit())),
                  6,
                  RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .doubleValue();
    }
    return new ReportMetrics(
        latest.getRevenue(),
        latest.getOperatingProfit(),
        latest.getNetIncome(),
        yoyRevGrowth,
        yoyOpGrowth);
  }

  private String buildSummary(RoutingResult routing, List<MetricContribution> contributions) {
    if (contributions.isEmpty()) {
      return String.format(Locale.ROOT, "%s 테마로 라우팅되었습니다.", routing.themeName());
    }
    MetricContribution top = contributions.get(0);
    return String.format(
        Locale.ROOT,
        "%s 테마 영향. 주요 기여 지표: %s (β=%.4f).",
        routing.themeName(),
        top.displayName(),
        top.beta());
  }

  private double round(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
  }
}
