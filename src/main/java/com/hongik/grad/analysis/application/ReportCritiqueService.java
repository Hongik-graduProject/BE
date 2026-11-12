package com.hongik.grad.analysis.application;

import com.hongik.grad.analysis.client.MlPredictionClient;
import com.hongik.grad.analysis.client.dto.MlPredictionJson;
import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.dto.CritiqueItem;
import com.hongik.grad.analysis.dto.CritiqueResponse;
import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.themes.domain.ThemeMetricSensitivity;
import com.hongik.grad.themes.domain.ThemeMetricSensitivityRepository;
import com.hongik.grad.timemachine.application.TrackRecordService;
import com.hongik.grad.timemachine.dto.TrackRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 반박 모드(제안서 §2, 계획 §5). LLM 없이 DB 컬럼만으로 리포트 스스로의 약점을 센다. 점수·등급(§2-3)과 항목은 같은 r_squared/표본/Δ 값을 공유해
 * 서로 어긋나지 않는다.
 */
@Service
public class ReportCritiqueService {

  private static final int TOP_CONTRIBUTIONS = 5;

  private final AnalysisReportRepository analysisReportRepository;
  private final ThemeMetricSensitivityRepository sensitivityRepository;
  private final MlPredictionClient mlPredictionClient;
  private final TrackRecordService trackRecordService;

  public ReportCritiqueService(
      AnalysisReportRepository analysisReportRepository,
      ThemeMetricSensitivityRepository sensitivityRepository,
      MlPredictionClient mlPredictionClient,
      TrackRecordService trackRecordService) {
    this.analysisReportRepository = analysisReportRepository;
    this.sensitivityRepository = sensitivityRepository;
    this.mlPredictionClient = mlPredictionClient;
    this.trackRecordService = trackRecordService;
  }

  /**
   * 트랜잭션을 걸지 않는다. 리포트는 fetch-join 한 번(그 자체가 짧은 tx)으로 다 읽고, 그 뒤 PAST_MISSES(전적)·ML 커버리지는 네트워크/INSERT
   * 를 동반하므로 tx 밖에서 호출한다. tx 안에서 부르면 read-only tx 에 INSERT 가 끼어 실패하고, 네트워크가 느릴 때 커넥션 풀을 고갈시킨다.
   */
  public CritiqueResponse critique(Long reportId) {
    AnalysisReport report =
        analysisReportRepository
            .findWithAssociationsById(reportId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "보고서를 찾을 수 없습니다"));

    List<CritiqueItem> items = new ArrayList<>();
    int score = 0;

    // (1)(2) 이 리포트가 실제로 쓴 β 행 = 적용 테마·stage2 버전의 민감도 중, 기여도에 등장하는 metric_code 만.
    List<ThemeMetricSensitivity> rows = usedSensitivities(report);
    Double minR =
        rows.stream()
            .map(ThemeMetricSensitivity::getRSquared)
            .filter(v -> v != null)
            .min(Comparator.naturalOrder())
            .orElse(null);
    Integer minSample =
        rows.stream()
            .map(ThemeMetricSensitivity::getSampleSize)
            .filter(v -> v != null)
            .min(Comparator.naturalOrder())
            .orElse(null);

    score += minR == null ? 0 : (minR >= 0.5 ? 2 : minR >= 0.3 ? 1 : 0);
    if (minR != null && minR < 0.3) {
      items.add(
          new CritiqueItem(
              "LOW_R_SQUARED",
              "warning",
              "이 테마의 설명력이 낮습니다",
              "R² = %.2f (권장 0.5 이상)".formatted(minR)));
    }

    score += minSample == null ? 0 : (minSample >= 300 ? 2 : minSample >= 100 ? 1 : 0);
    if (minSample != null && minSample < 100) {
      items.add(
          new CritiqueItem(
              "SMALL_SAMPLE",
              "warning",
              "추정 표본이 적어 계수가 불안정합니다",
              "표본 크기 = %d (권장 100 이상)".formatted(minSample)));
    }

    // (3) 기여도 상위 5 개 중 Δ = 0 개수.
    long zeroDelta = zeroDeltaCount(report.getMetricContributions());
    score += zeroDelta == 0 ? 2 : zeroDelta <= 1 ? 1 : 0;
    if (zeroDelta >= 2) {
      items.add(
          new CritiqueItem(
              "MISSING_DELTA",
              "warning",
              "재무 데이터가 비어 기여도를 계산하지 못한 지표가 있습니다",
              "상위 5개 기여 지표 중 %d개의 Δ가 0".formatted(zeroDelta)));
    }

    // (3-b) ML feature_coverage. ML 이 없거나 값이 없으면 점수 0 + info (실패해도 던지지 않는다).
    Double coverage =
        mlPredictionClient
            .fetchPrediction(report.getStock().getTicker())
            .map(MlPredictionJson::featureCoverage)
            .orElse(null);
    score += (coverage != null && coverage >= 0.9) ? 1 : 0;
    if (coverage == null) {
      items.add(
          new CritiqueItem(
              "LOW_FEATURE_COVERAGE",
              "info",
              "데이터 결측 여부를 확인하지 못했습니다",
              "ML 서비스가 응답하지 않아 feature_coverage를 확인하지 못했습니다"));
    } else if (coverage < 0.9) {
      items.add(
          new CritiqueItem(
              "LOW_FEATURE_COVERAGE",
              "warning",
              "일부 피처가 결측입니다",
              "feature_coverage = %.2f (권장 0.9 이상)".formatted(coverage)));
    }

    // (4) 과거 오답. 공시가 없으면 유형 null 로 전체 전적을 쓴다.
    TrackRecord record =
        trackRecordService.forTicker(report.getStock().getTicker(), disclosureType(report));
    score += (record.hitRate() != null && record.hitRate() >= 0.6 && record.judged() >= 10) ? 1 : 0;
    if (record.hitRate() == null) {
      items.add(
          new CritiqueItem(
              "PAST_MISSES",
              "info",
              "과거 전적 표본이 부족합니다",
              "표본 부족 (판정 %d건)".formatted(record.judged())));
    } else if (record.hitRate() < 0.6) {
      items.add(
          new CritiqueItem(
              "PAST_MISSES",
              "warning",
              "과거 이 종목·공시 유형에서 모델이 자주 틀렸습니다",
              "과거 적중률 %.0f%% (%d판정 %d적중)"
                  .formatted(record.hitRate() * 100, record.judged(), record.hits())));
    }

    // (5) 구조적 한계: 조건과 무관하게 항상 표시.
    items.add(
        new CritiqueItem(
            "STRUCTURAL_NO_ZSCORE", "info", "지표 기여도에 z-score 정규화가 없습니다", "단위가 다른 Δ를 그대로 더합니다"));
    items.add(
        new CritiqueItem(
            "STRUCTURAL_CAR_BAND",
            "info",
            "신뢰구간은 아직 자리표시자입니다",
            "car_lower·car_upper = expected_car ± 2 고정"));
    items.add(
        new CritiqueItem(
            "STRUCTURAL_PRIMARY_THEME",
            "info",
            "1순위 테마만 사용합니다",
            "PrimaryFixedRouter는 rank 1 테마만 보고 2·3순위 영향을 버립니다"));

    return new CritiqueResponse(grade(score), score, items);
  }

  private List<ThemeMetricSensitivity> usedSensitivities(AnalysisReport report) {
    Long themeId = report.getAppliedTheme().getId();
    String version = report.getStage2ModelVersion();
    if (themeId == null || version == null) {
      return List.of();
    }
    Set<String> usedCodes =
        contributions(report).stream()
            .map(MetricContribution::metricCode)
            .map(c -> c.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    if (usedCodes.isEmpty()) {
      return List.of();
    }
    return sensitivityRepository
        .findByThemeIdAndModelVersionOrderByRankAsc(themeId, version)
        .stream()
        .filter(s -> usedCodes.contains(s.getMetricCode().toLowerCase(Locale.ROOT)))
        .toList();
  }

  /**
   * 상위 5 개를 |contribution| 이 아니라 |beta| 로 고른다. contribution = beta × delta 라서 Δ=0 인 결측 행은
   * contribution 이 0 으로 맨 아래로 밀려, 지표가 5 개를 넘으면 창 밖으로 빠져 영영 세어지지 않는다(=규칙이 항상 침묵). 결측을 드러내는 게 이 규칙의
   * 목적이므로 민감도 크기 기준으로 고른 뒤 Δ=0 을 센다.
   */
  private long zeroDeltaCount(List<MetricContribution> contributions) {
    return (contributions == null ? List.<MetricContribution>of() : contributions)
        .stream()
            .sorted(
                Comparator.comparingDouble((MetricContribution c) -> Math.abs(c.beta())).reversed())
            .limit(TOP_CONTRIBUTIONS)
            .filter(c -> c.delta() == 0.0)
            .count();
  }

  private List<MetricContribution> contributions(AnalysisReport report) {
    return report.getMetricContributions() == null ? List.of() : report.getMetricContributions();
  }

  private DisclosureType disclosureType(AnalysisReport report) {
    Disclosure disclosure = report.getDisclosure();
    return disclosure == null ? null : disclosure.getType();
  }

  private String grade(int score) {
    return score >= 7 ? "상" : score >= 4 ? "중" : "하";
  }
}
