package com.hongik.grad.analysis.application;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.domain.ReportReadStatus;
import com.hongik.grad.analysis.domain.ReportReadStatusRepository;
import com.hongik.grad.analysis.dto.AppliedThemeBriefResponse;
import com.hongik.grad.analysis.dto.AppliedThemeResponse;
import com.hongik.grad.analysis.dto.PaginationMeta;
import com.hongik.grad.analysis.dto.ReportDetailResponse;
import com.hongik.grad.analysis.dto.ReportListItemResponse;
import com.hongik.grad.analysis.dto.ReportListResponse;
import com.hongik.grad.analysis.dto.SensitiveMetricResponse;
import com.hongik.grad.analysis.dto.SourceDisclosureResponse;
import com.hongik.grad.analysis.dto.ThemeItemResponse;
import com.hongik.grad.analysis.dto.ThemesResponse;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.themes.application.ThemeQueryService;
import com.hongik.grad.themes.domain.FinancialMetricCatalog;
import com.hongik.grad.themes.domain.FinancialMetricCatalogRepository;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.dto.SensitivityView;
import com.hongik.grad.themes.dto.StockThemeView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisQueryService {

  private static final int MAX_PAGE_SIZE = 100;
  private static final String UNIVERSE_OUTSIDE_MESSAGE = "ThemeSense 학습 universe 외 종목 — 테마 분류 대기 중";

  private final ThemeQueryService themeQueryService;
  private final AnalysisReportRepository analysisReportRepository;
  private final ReportReadStatusRepository reportReadStatusRepository;
  private final ReportReadStatusWriter reportReadStatusWriter;
  private final StockRepository stockRepository;
  private final StockThemeRepository stockThemeRepository;
  private final FinancialMetricCatalogRepository metricCatalogRepository;
  private final String stage2Version;

  public AnalysisQueryService(
      ThemeQueryService themeQueryService,
      AnalysisReportRepository analysisReportRepository,
      ReportReadStatusRepository reportReadStatusRepository,
      ReportReadStatusWriter reportReadStatusWriter,
      StockRepository stockRepository,
      StockThemeRepository stockThemeRepository,
      FinancialMetricCatalogRepository metricCatalogRepository,
      @Value("${themesense.stage2-version:themesense-stage2-v1.0}") String stage2Version) {
    this.themeQueryService = themeQueryService;
    this.analysisReportRepository = analysisReportRepository;
    this.reportReadStatusRepository = reportReadStatusRepository;
    this.reportReadStatusWriter = reportReadStatusWriter;
    this.stockRepository = stockRepository;
    this.stockThemeRepository = stockThemeRepository;
    this.metricCatalogRepository = metricCatalogRepository;
    this.stage2Version = stage2Version;
  }

  /** C5: ticker existence checked first → 404 if missing. M1: name, modelVersion, predictedAt. */
  @Transactional(readOnly = true)
  public ThemesResponse getThemes(String ticker) {
    Stock stock =
        stockRepository
            .findById(ticker)
            .orElseThrow(
                () -> new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다: " + ticker));

    List<StockTheme> rawThemes = stockThemeRepository.findByStockTickerOrderByRankAsc(ticker);

    if (rawThemes.isEmpty()) {
      return new ThemesResponse(
          ticker, stock.getName(), List.of(), null, null, UNIVERSE_OUTSIDE_MESSAGE);
    }

    StockTheme firstTheme = rawThemes.get(0);
    String modelVersion = firstTheme.getModelVersion();
    OffsetDateTime predictedAt = firstTheme.getPredictedAt();

    List<StockThemeView> top3 = themeQueryService.findTop3ByTicker(ticker);
    List<ThemeItemResponse> items =
        top3.stream()
            .map(
                view -> {
                  List<SensitiveMetricResponse> top5 =
                      view.isPrimary()
                          ? themeQueryService
                              .findTop5SensitivityByThemeAndVersion(view.themeId(), stage2Version)
                              .stream()
                              .map(s -> toSensitiveMetric(s, stage2Version))
                              .toList()
                          : List.of();
                  return new ThemeItemResponse(
                      view.themeId(),
                      view.themeName(),
                      view.rank(),
                      view.probability(),
                      view.isPrimary(),
                      top5);
                })
            .toList();

    return new ThemesResponse(ticker, stock.getName(), items, modelVersion, predictedAt, null);
  }

  /**
   * C5: ticker existence checked. M2: 1-based page, PaginationMeta, reports key. M6: sort/page
   * validation.
   */
  @Transactional(readOnly = true)
  public ReportListResponse listReports(
      UUID userId, String ticker, int page, int size, String sort) {

    Stock stock =
        stockRepository
            .findById(ticker)
            .orElseThrow(
                () -> new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다: " + ticker));

    if (!"latest".equalsIgnoreCase(sort) && !"oldest".equalsIgnoreCase(sort)) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "sort must be latest|oldest");
    }
    if (page < 1) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "page must be >= 1");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new DomainException(
          ErrorCode.INVALID_PARAM, "size must be between 1 and " + MAX_PAGE_SIZE);
    }

    // Convert 1-based to 0-based for Spring Data
    int zeroBasedPage = page - 1;
    Sort.Direction direction =
        "oldest".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
    PageRequest pageRequest =
        PageRequest.of(zeroBasedPage, size, Sort.by(direction, "generatedAt"));

    Page<AnalysisReport> reports = analysisReportRepository.findByStockTicker(ticker, pageRequest);

    List<Long> ids = reports.getContent().stream().map(AnalysisReport::getId).toList();
    Set<Long> readIds =
        ids.isEmpty()
            ? Collections.emptySet()
            : reportReadStatusRepository.findByUserIdAndReportIdIn(userId, ids).stream()
                .map(ReportReadStatus::getReportId)
                .collect(Collectors.toSet());

    List<ReportListItemResponse> items =
        reports.getContent().stream().map(r -> toListItem(r, readIds.contains(r.getId()))).toList();

    PaginationMeta pagination = PaginationMeta.of(page, size, reports.getTotalElements());
    return new ReportListResponse(ticker, stock.getName(), items, pagination);
  }

  /** M5: read-only for the detail fetch; mark-read runs in a separate REQUIRES_NEW transaction. */
  @Transactional(readOnly = true)
  public ReportDetailResponse getReportDetail(UUID userId, Long reportId) {
    AnalysisReport report =
        analysisReportRepository
            .findById(reportId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "보고서를 찾을 수 없습니다"));

    reportReadStatusWriter.markRead(userId, reportId);
    return toDetail(report, true);
  }

  private ReportListItemResponse toListItem(AnalysisReport report, boolean isRead) {
    return new ReportListItemResponse(
        report.getId(),
        report.getTitle(),
        report.getSummary(),
        new AppliedThemeBriefResponse(
            report.getAppliedTheme().getId(), report.getAppliedTheme().getName()),
        toDouble(report.getExpectedCar()),
        isRead,
        report.getGeneratedAt());
  }

  private ReportDetailResponse toDetail(AnalysisReport report, boolean isRead) {
    SourceDisclosureResponse sourceDisclosure = buildSourceDisclosure(report.getDisclosure());
    return new ReportDetailResponse(
        report.getId(),
        report.getStock().getTicker(),
        report.getStock().getName(),
        report.getTitle(),
        new AppliedThemeResponse(
            report.getAppliedTheme().getId(),
            report.getAppliedTheme().getName(),
            toDouble(report.getThemeRoutingScore())),
        sourceDisclosure,
        report.getSummary(),
        toDouble(report.getExpectedCar()),
        toDouble(report.getCarLower()),
        toDouble(report.getCarUpper()),
        report.getMetricContributions(),
        report.getKeyFindings(),
        report.getMetrics(),
        report.getStage1ModelVersion(),
        report.getStage2ModelVersion(),
        isRead,
        report.getGeneratedAt());
  }

  private SourceDisclosureResponse buildSourceDisclosure(Disclosure disclosure) {
    if (disclosure == null) {
      return null;
    }
    String dartUrl = disclosure.getDartUrl();
    if (dartUrl == null && disclosure.getDartFilingId() != null) {
      dartUrl = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + disclosure.getDartFilingId();
    }
    return new SourceDisclosureResponse(
        disclosure.getId(), disclosure.getTitle(), disclosure.getFiledAt(), dartUrl);
  }

  /** C2+C3: use MetricCanonicalCode to resolve catalogKey for display_name lookup. */
  private SensitiveMetricResponse toSensitiveMetric(SensitivityView v, String version) {
    Optional<MetricCanonicalCode> canonicalOpt =
        MetricCanonicalCode.fromAlias(v.metricCode().toLowerCase(Locale.ROOT));
    String catalogKey =
        canonicalOpt
            .map(MetricCanonicalCode::catalogKey)
            .orElse(v.metricCode().toLowerCase(Locale.ROOT));
    String displayName =
        metricCatalogRepository
            .findById(catalogKey)
            .map(FinancialMetricCatalog::getNameKo)
            .orElse(v.metricCode());
    return new SensitiveMetricResponse(v.metricCode(), displayName, v.beta(), v.rank());
  }

  private Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
