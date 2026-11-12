package com.hongik.grad.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.analysis.client.MlPredictionClient;
import com.hongik.grad.analysis.client.dto.MlPredictionJson;
import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.dto.CritiqueItem;
import com.hongik.grad.analysis.dto.CritiqueResponse;
import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeMetricSensitivity;
import com.hongik.grad.themes.domain.ThemeMetricSensitivityRepository;
import com.hongik.grad.timemachine.application.TrackRecordService;
import com.hongik.grad.timemachine.dto.TrackRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 반박 모드 단위 테스트. Spring 없이 순수 Mockito 로 규칙·점수·등급을 검증한다. 네트워크(Naver/ML)를 부르지 않도록 협력자를 모두 모킹한다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportCritiqueServiceTest {

  @Mock private AnalysisReportRepository analysisReportRepository;
  @Mock private ThemeMetricSensitivityRepository sensitivityRepository;
  @Mock private MlPredictionClient mlPredictionClient;
  @Mock private TrackRecordService trackRecordService;

  private ReportCritiqueService service;

  @Mock private AnalysisReport report;
  @Mock private Stock stock;
  @Mock private Theme theme;
  @Mock private Disclosure disclosure;

  private static final long ID = 1L;
  private static final String TICKER = "005930";
  private static final String VERSION = "v1";

  @BeforeEach
  void setUp() {
    service =
        new ReportCritiqueService(
            analysisReportRepository,
            sensitivityRepository,
            mlPredictionClient,
            trackRecordService);

    when(report.getStock()).thenReturn(stock);
    when(stock.getTicker()).thenReturn(TICKER);
    when(report.getAppliedTheme()).thenReturn(theme);
    when(theme.getId()).thenReturn(10L);
    when(report.getStage2ModelVersion()).thenReturn(VERSION);
    when(report.getDisclosure()).thenReturn(disclosure);
    when(disclosure.getType()).thenReturn(DisclosureType.QUARTERLY);
    when(analysisReportRepository.findWithAssociationsById(ID)).thenReturn(Optional.of(report));

    // 기본: 모든 근거가 양호한 리포트 (점수 8, 등급 상).
    stubSensitivities(sens(0.6, 400));
    stubContributions(1.0, 1.0, 1.0, 1.0, 1.0);
    stubCoverage(0.95);
    stubTrackRecord(0.7, 12);
  }

  // ---- 조회/에러 ----

  @Test
  @DisplayName("없는 report_id → NOT_FOUND")
  void unknownReport_notFound() {
    when(analysisReportRepository.findWithAssociationsById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.critique(99L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("보고서");
  }

  // ---- 등급 경계 ----

  @Test
  @DisplayName("모든 근거 양호 → 점수 8, 등급 상, 항목은 구조적 3개뿐")
  void allGood_gradeHigh() {
    CritiqueResponse r = service.critique(ID);
    assertThat(r.score()).isEqualTo(8);
    assertThat(r.grade()).isEqualTo("상");
    assertThat(r.items()).hasSize(3);
    assertThat(codes(r))
        .containsExactly("STRUCTURAL_NO_ZSCORE", "STRUCTURAL_CAR_BAND", "STRUCTURAL_PRIMARY_THEME");
  }

  @Test
  @DisplayName("점수 7 → 상 (경계)")
  void scoreSeven_high() {
    stubSensitivities(sens(0.5, 300)); // 2 + 2
    stubContributions(1.0, 1.0, 1.0, 1.0, 1.0); // zeroDelta 0 → 2
    stubCoverage(0.95); // 1
    stubTrackRecord(0.5, 12); // past 미달 → 0, 경고 발생
    CritiqueResponse r = service.critique(ID);
    assertThat(r.score()).isEqualTo(7);
    assertThat(r.grade()).isEqualTo("상");
    assertThat(codes(r)).contains("PAST_MISSES");
  }

  @Test
  @DisplayName("점수 6 → 중 (경계)")
  void scoreSix_mid() {
    stubSensitivities(sens(0.5, 300)); // 4
    stubContributions(1.0, 1.0, 1.0, 1.0, 1.0); // 2
    stubCoverage(0.5); // 0, 경고
    stubTrackRecord(0.5, 12); // 0
    CritiqueResponse r = service.critique(ID);
    assertThat(r.score()).isEqualTo(6);
    assertThat(r.grade()).isEqualTo("중");
  }

  @Test
  @DisplayName("점수 4 → 중 (경계)")
  void scoreFour_mid() {
    stubSensitivities(sens(0.3, 100)); // 1 + 1
    stubContributions(0.0, 1.0, 1.0, 1.0, 1.0); // zeroDelta 1 → 1
    stubCoverage(0.95); // 1
    stubTrackRecord(0.5, 12); // 0
    CritiqueResponse r = service.critique(ID);
    assertThat(r.score()).isEqualTo(4);
    assertThat(r.grade()).isEqualTo("중");
  }

  @Test
  @DisplayName("점수 3 → 하 (경계)")
  void scoreThree_low() {
    stubSensitivities(sens(0.3, 100)); // 1 + 1
    stubContributions(0.0, 1.0, 1.0, 1.0, 1.0); // 1
    stubCoverage(0.5); // 0
    stubTrackRecord(0.5, 12); // 0
    CritiqueResponse r = service.critique(ID);
    assertThat(r.score()).isEqualTo(3);
    assertThat(r.grade()).isEqualTo("하");
  }

  // ---- 각 근거 발화/침묵 ----

  @Test
  @DisplayName("LOW_R_SQUARED: 최소 R² < 0.3 → 경고 + R² 점수항 0 (항목·점수 일치)")
  void lowRSquared_warningAndConsistentScore() {
    stubSensitivities(sens(0.2, 400)); // r항 0, sample항 2
    stubContributions(1.0, 1.0, 1.0, 1.0, 1.0); // 2
    stubCoverage(0.95); // 1
    stubTrackRecord(0.7, 12); // 1
    CritiqueResponse r = service.critique(ID);
    assertThat(codes(r)).contains("LOW_R_SQUARED");
    assertThat(severity(r, "LOW_R_SQUARED")).isEqualTo("warning");
    assertThat(r.score()).isEqualTo(6); // r항이 0 이어야 성립
  }

  @Test
  @DisplayName("SMALL_SAMPLE: 최소 표본 < 100 → 경고")
  void smallSample_warning() {
    stubSensitivities(sens(0.6, 50));
    CritiqueResponse r = service.critique(ID);
    assertThat(codes(r)).contains("SMALL_SAMPLE");
    assertThat(severity(r, "SMALL_SAMPLE")).isEqualTo("warning");
  }

  @Test
  @DisplayName("MISSING_DELTA: 상위5 중 Δ=0 이 2개 → 경고, 1개면 침묵")
  void missingDelta_firesOnTwoZeros() {
    stubContributions(0.0, 0.0, 1.0, 1.0, 1.0);
    assertThat(codes(service.critique(ID))).contains("MISSING_DELTA");

    stubContributions(0.0, 1.0, 1.0, 1.0, 1.0);
    assertThat(codes(service.critique(ID))).doesNotContain("MISSING_DELTA");
  }

  @Test
  @DisplayName(
      "MISSING_DELTA: 지표 5개 초과 + 결측(Δ=0) 행의 |beta| 가 크면 top-5 에 들어 발화 (contribution 정렬 버그 회귀)")
  void missingDelta_countsHollowRowsByBetaNotContribution() {
    // contribution = beta × delta 라 Δ=0 행은 contribution 0 으로 맨 아래. |contribution| 로 고르면 7개 중
    // 창(5) 밖으로 빠져 0개로 세어졌다. |beta| 로 골라야 큰 beta 의 결측 2개가 top-5 에 들어온다.
    when(report.getMetricContributions())
        .thenReturn(
            List.of(
                contrib(0.90, 0.0), // 결측, beta 최고
                contrib(0.85, 0.0), // 결측, beta 2위
                contrib(0.50, 2.0),
                contrib(0.40, 2.0),
                contrib(0.30, 2.0),
                contrib(0.20, 2.0),
                contrib(0.10, 2.0)));
    stubSensitivities(sens(0.6, 400)); // 4
    stubCoverage(0.95); // 1
    stubTrackRecord(0.7, 12); // 1
    CritiqueResponse r = service.critique(ID);
    assertThat(codes(r)).contains("MISSING_DELTA");
    assertThat(evidence(r, "MISSING_DELTA")).contains("2개");
    assertThat(r.score()).isEqualTo(6); // zeroDelta 2 → 해당 점수항 0 (4 + 0 + 1 + 1)
  }

  @Test
  @DisplayName("LOW_FEATURE_COVERAGE: ML 미응답 → info + coverage 점수항 0, 예외 없음")
  void mlUnavailable_infoNoThrow() {
    when(mlPredictionClient.fetchPrediction(TICKER)).thenReturn(Optional.empty());
    stubSensitivities(sens(0.6, 400)); // 4
    stubContributions(1.0, 1.0, 1.0, 1.0, 1.0); // 2
    stubTrackRecord(0.7, 12); // 1
    CritiqueResponse r = service.critique(ID);
    assertThat(codes(r)).contains("LOW_FEATURE_COVERAGE");
    assertThat(severity(r, "LOW_FEATURE_COVERAGE")).isEqualTo("info");
    assertThat(r.score()).isEqualTo(7); // coverage 항 0
  }

  @Test
  @DisplayName("LOW_FEATURE_COVERAGE: coverage < 0.9 → 경고")
  void lowCoverage_warning() {
    stubCoverage(0.5);
    CritiqueResponse r = service.critique(ID);
    assertThat(severity(r, "LOW_FEATURE_COVERAGE")).isEqualTo("warning");
  }

  @Test
  @DisplayName("PAST_MISSES: 적중률 < 0.6 → 경고")
  void pastMisses_warning() {
    stubTrackRecord(0.4, 15);
    CritiqueResponse r = service.critique(ID);
    assertThat(severity(r, "PAST_MISSES")).isEqualTo("warning");
  }

  @Test
  @DisplayName("PAST_MISSES: 표본 부족(judged<10, hitRate null) → info")
  void pastMisses_infoWhenInsufficient() {
    stubTrackRecord(null, 5);
    CritiqueResponse r = service.critique(ID);
    assertThat(severity(r, "PAST_MISSES")).isEqualTo("info");
    assertThat(evidence(r, "PAST_MISSES")).contains("표본 부족");
  }

  // ---- 엣지 ----

  @Test
  @DisplayName("민감도 행이 전혀 없으면 R²/표본 항목 없음, 구조적 항목은 유지, 등급 하로 수렴")
  void zeroSensitivityRows_structuralOnly() {
    when(sensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(10L, VERSION))
        .thenReturn(List.of());
    stubContributions(0.0, 1.0, 1.0, 1.0, 1.0); // zeroDelta 1 → 1
    stubCoverage(0.5); // 0
    stubTrackRecord(0.4, 15); // 0
    CritiqueResponse r = service.critique(ID);
    assertThat(codes(r)).doesNotContain("LOW_R_SQUARED", "SMALL_SAMPLE");
    assertThat(codes(r))
        .contains("STRUCTURAL_NO_ZSCORE", "STRUCTURAL_CAR_BAND", "STRUCTURAL_PRIMARY_THEME");
    assertThat(r.score()).isEqualTo(1); // r0 sample0 zeroDelta1 coverage0 past0
    assertThat(r.grade()).isEqualTo("하");
  }

  @Test
  @DisplayName("기여도에 없는 metric_code 의 β 행은 무시한다 (이 리포트가 쓴 행만)")
  void onlyContributionCodesCount() {
    // 기여도는 per 만. 레포지토리는 per(r=0.2)·pbr(r=0.9) 둘 다 돌려줘도 per 만 세어 최소 R²=0.2.
    stubContributions(1.0, 1.0, 1.0, 1.0, 1.0); // code "per"
    when(sensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(10L, VERSION))
        .thenReturn(List.of(sensCode("per", 0.2, 400), sensCode("pbr", 0.9, 400)));
    CritiqueResponse r = service.critique(ID);
    assertThat(codes(r)).contains("LOW_R_SQUARED");
  }

  @Test
  @DisplayName("공시가 없는 리포트 → 유형 null 로 전적 조회, 예외 없음")
  void noDisclosure_usesNullType() {
    when(report.getDisclosure()).thenReturn(null);
    when(trackRecordService.forTicker(eq(TICKER), isNull())).thenReturn(track(0.7, 12));
    CritiqueResponse r = service.critique(ID);
    assertThat(r).isNotNull();
    verify(trackRecordService).forTicker(TICKER, null);
  }

  // ---- 헬퍼 ----

  private void stubSensitivities(ThemeMetricSensitivity... rows) {
    when(sensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(10L, VERSION))
        .thenReturn(List.of(rows));
  }

  private void stubContributions(double... deltas) {
    when(report.getMetricContributions()).thenReturn(contribs(deltas));
  }

  private void stubCoverage(double coverage) {
    when(mlPredictionClient.fetchPrediction(TICKER)).thenReturn(Optional.of(ml(coverage)));
  }

  private void stubTrackRecord(Double hitRate, int judged) {
    when(trackRecordService.forTicker(TICKER, DisclosureType.QUARTERLY))
        .thenReturn(track(hitRate, judged));
  }

  private static List<MetricContribution> contribs(double... deltas) {
    List<MetricContribution> list = new ArrayList<>();
    for (int i = 0; i < deltas.length; i++) {
      list.add(new MetricContribution("per", "주가수익비율", 0.3, null, deltas[i], i + 1));
    }
    return list;
  }

  private static MetricContribution contrib(double beta, double delta) {
    return new MetricContribution("per", "주가수익비율", beta, null, delta, beta * delta);
  }

  private static ThemeMetricSensitivity sens(double rSquared, int sampleSize) {
    return sensCode("per", rSquared, sampleSize);
  }

  private static ThemeMetricSensitivity sensCode(String code, double rSquared, int sampleSize) {
    ThemeMetricSensitivity s = new ThemeMetricSensitivity(null, code, 0.3, 0.3, 1);
    s.setRSquared(rSquared);
    s.setSampleSize(sampleSize);
    return s;
  }

  private static MlPredictionJson ml(double coverage) {
    return new MlPredictionJson(null, null, null, null, null, null, null, null, null, coverage);
  }

  private static TrackRecord track(Double hitRate, int judged) {
    int hits = hitRate == null ? 0 : (int) Math.round(hitRate * judged);
    return new TrackRecord(judged, judged, hits, hitRate, 0, 0, 0);
  }

  private static List<String> codes(CritiqueResponse r) {
    return r.items().stream().map(CritiqueItem::code).toList();
  }

  private static String severity(CritiqueResponse r, String code) {
    return r.items().stream()
        .filter(i -> i.code().equals(code))
        .map(CritiqueItem::severity)
        .findFirst()
        .orElseThrow();
  }

  private static String evidence(CritiqueResponse r, String code) {
    return r.items().stream()
        .filter(i -> i.code().equals(code))
        .map(CritiqueItem::evidence)
        .findFirst()
        .orElseThrow();
  }
}
