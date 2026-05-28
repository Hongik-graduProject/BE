package com.hongik.grad.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.domain.ReportReadStatus;
import com.hongik.grad.analysis.domain.ReportReadStatusRepository;
import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.analysis.dto.ReportMetrics;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class AnalysisReportRepositoryTest {

  @Autowired private AnalysisReportRepository analysisReportRepository;
  @Autowired private ReportReadStatusRepository reportReadStatusRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private ThemeRepository themeRepository;
  @Autowired private DisclosureRepository disclosureRepository;
  @Autowired private TestEntityManager em;

  private Stock stock;
  private Theme theme;

  @BeforeEach
  void setUp() {
    // Clean up analysis data first to ensure test isolation
    analysisReportRepository.deleteAll();
    disclosureRepository.deleteAll();
    em.flush();
    em.clear();

    stock = stockRepository.save(new Stock("005930", "삼성전자", Market.KOSPI));
    Theme unsaved = new Theme("AI/반도체");
    unsaved.setSource("test");
    theme = themeRepository.save(unsaved);
    em.flush();
    em.clear();
    stock = stockRepository.findById("005930").orElseThrow();
    theme = themeRepository.findById(theme.getId()).orElseThrow();
  }

  @Test
  @DisplayName("AnalysisReport JSONB 컬럼 round-trip 저장/조회 (new schema)")
  void jsonbRoundTrip() {
    List<MetricContribution> contributions =
        List.of(
            new MetricContribution("per", "주가수익비율", 0.35, "12.50", 1.2, 0.42),
            new MetricContribution("roe", "자기자본이익률", -0.28, "20.00%", 0.5, -0.14));
    List<String> findings = List.of("매출 증가 — 전년 대비 12%", "HBM 수주 호조");
    ReportMetrics metrics =
        new ReportMetrics(78_000_000_000_000L, 9_200_000_000_000L, 6_800_000_000_000L, 8.3, 12.1);

    AnalysisReport report = new AnalysisReport(stock, theme, "삼성전자 2024Q4 분석");
    report.setExpectedCar(new BigDecimal("0.2800"));
    report.setCarLower(new BigDecimal("-1.7200"));
    report.setCarUpper(new BigDecimal("2.2800"));
    report.setThemeRoutingScore(new BigDecimal("0.8500"));
    report.setMetricContributions(contributions);
    report.setKeyFindings(findings);
    report.setMetrics(metrics);
    report.setStage1ModelVersion("stage1-v1.0");
    report.setStage2ModelVersion("stage2-v1.0");
    report.setSummary("AI/반도체 테마 영향");

    AnalysisReport saved = analysisReportRepository.save(report);
    em.flush();
    em.clear();

    AnalysisReport found = analysisReportRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getStock().getTicker()).isEqualTo("005930");
    assertThat(found.getAppliedTheme().getName()).isEqualTo("AI/반도체");
    assertThat(found.getExpectedCar()).isEqualByComparingTo(new BigDecimal("0.2800"));
    assertThat(found.getMetricContributions()).hasSize(2);
    assertThat(found.getMetricContributions().get(0).metricCode()).isEqualTo("per");
    assertThat(found.getMetricContributions().get(0).beta()).isEqualTo(0.35);
    assertThat(found.getMetricContributions().get(0).rawValue()).isEqualTo("12.50");
    assertThat(found.getKeyFindings()).hasSize(2);
    assertThat(found.getKeyFindings().get(0)).isEqualTo("매출 증가 — 전년 대비 12%");
    assertThat(found.getMetrics().revenue()).isEqualTo(78_000_000_000_000L);
    assertThat(found.getStage1ModelVersion()).isEqualTo("stage1-v1.0");
    assertThat(found.getGeneratedAt()).isNotNull();
  }

  @Test
  @DisplayName("UNIQUE(ticker, disclosure_id): findByStockTickerAndDisclosureId는 중복 존재 감지")
  void uniqueTickerDisclosureConstraint() {
    // Use nanoTime-based ID to avoid dart_filing_id conflicts across test runs
    String uniqueFilingId = "UNIQ-" + System.nanoTime();
    Disclosure disclosure =
        disclosureRepository.save(
            new Disclosure(
                stock, uniqueFilingId, "분기보고서", DisclosureType.QUARTERLY, OffsetDateTime.now()));

    AnalysisReport report = new AnalysisReport(stock, theme, "보고서");
    report.setDisclosure(disclosure);
    AnalysisReport saved = analysisReportRepository.save(report);

    // Verify the duplicate detection query (used by generator) returns the existing report
    var found =
        analysisReportRepository.findByStockTickerAndDisclosureId("005930", disclosure.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  @DisplayName("ReportReadStatus 저장 후 user_id + report_id로 조회된다")
  void readStatusRoundTrip() {
    UUID userId = UUID.randomUUID();
    // profile row must exist (FK report_read_status -> profiles).
    em.getEntityManager()
        .createNativeQuery("INSERT INTO profiles (id, nickname) VALUES (:id, :nickname)")
        .setParameter("id", userId)
        .setParameter("nickname", "tester")
        .executeUpdate();

    AnalysisReport report = new AnalysisReport(stock, theme, "보고서 A");
    AnalysisReport saved = analysisReportRepository.save(report);
    em.flush();

    reportReadStatusRepository.save(new ReportReadStatus(userId, saved.getId()));
    em.flush();
    em.clear();

    Optional<ReportReadStatus> found =
        reportReadStatusRepository.findByUserIdAndReportId(userId, saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
    assertThat(found.get().getReportId()).isEqualTo(saved.getId());
    assertThat(found.get().getReadAt()).isNotNull();
    assertThat(reportReadStatusRepository.existsByUserIdAndReportId(userId, saved.getId()))
        .isTrue();
  }
}
