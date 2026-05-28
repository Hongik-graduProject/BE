package com.hongik.grad.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.domain.ReportReadStatusRepository;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.JwtTestFactory;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.themes.application.ThemeIngestService;
import com.hongik.grad.themes.etl.ArtifactsLoader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class AnalysisGoldenPathIntegrationTest {

  private static final String STAGE1_VERSION = "themesense-stage1-v1.0";
  private static final String STAGE2_VERSION = "themesense-stage2-v1.0";

  @Autowired private MockMvc mockMvc;
  @Autowired private ArtifactsLoader artifactsLoader;
  @Autowired private ThemeIngestService themeIngestService;
  @Autowired private StockRepository stockRepository;
  @Autowired private FinancialStatementRepository financialStatementRepository;
  @Autowired private DisclosureRepository disclosureRepository;
  @Autowired private AnalysisReportRepository analysisReportRepository;
  @Autowired private ReportReadStatusRepository reportReadStatusRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  @PersistenceContext private EntityManager em;

  private UUID userId;
  private String token;

  @BeforeEach
  void setUp() throws IOException {
    transactionTemplate.executeWithoutResult(
        status -> {
          analysisReportRepository.deleteAll();
          reportReadStatusRepository.deleteAll();
          disclosureRepository.deleteAll();
        });

    transactionTemplate.executeWithoutResult(
        status -> {
          if (!stockRepository.existsById("005930")) {
            stockRepository.save(new Stock("005930", "삼성전자", Market.KOSPI));
          }
        });

    seedThemeArtifacts();

    transactionTemplate.executeWithoutResult(status -> seedFinancials("005930"));

    userId = UUID.randomUUID();
    UUID finalUserId = userId;
    transactionTemplate.executeWithoutResult(
        status ->
            em.createNativeQuery("INSERT INTO profiles (id, nickname) VALUES (:id, :nickname)")
                .setParameter("id", finalUserId)
                .setParameter("nickname", "tester")
                .executeUpdate());

    token = "Bearer " + JwtTestFactory.validToken(userId, "tester@example.com");
  }

  @Test
  @DisplayName("Disclosure event → AnalysisReport 자동 생성 → GET 상세 → ReportReadStatus UPSERT")
  void goldenPath() throws Exception {
    transactionTemplate.executeWithoutResult(
        status -> {
          Stock stock = stockRepository.findById("005930").orElseThrow();
          Disclosure disclosure =
              disclosureRepository.save(
                  new Disclosure(
                      stock,
                      "20250401000099",
                      "분기보고서 (2025.03)",
                      DisclosureType.QUARTERLY,
                      OffsetDateTime.now()));
          eventPublisher.publishEvent(
              new DisclosureCreatedEvent(
                  disclosure.getId(),
                  "005930",
                  disclosure.getDartFilingId(),
                  disclosure.getTitle(),
                  DisclosureType.QUARTERLY,
                  disclosure.getFiledAt()));
        });

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .until(() -> !analysisReportRepository.findAll().isEmpty());

    Long reportId =
        transactionTemplate.execute(
            status -> {
              AnalysisReport report = analysisReportRepository.findAll().get(0);
              assertThat(report.getStock().getTicker()).isEqualTo("005930");
              assertThat(report.getAppliedTheme().getName()).isEqualTo("AI/반도체");
              assertThat(report.getMetricContributions()).isNotEmpty();
              return report.getId();
            });

    assertThat(reportReadStatusRepository.existsByUserIdAndReportId(userId, reportId)).isFalse();

    mockMvc
        .perform(get("/analysis/reports/" + reportId).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(reportId.intValue()))
        .andExpect(jsonPath("$.data.name").value("삼성전자"))
        .andExpect(jsonPath("$.data.applied_theme.name").value("AI/반도체"))
        .andExpect(jsonPath("$.data.is_read").value(true));

    assertThat(reportReadStatusRepository.existsByUserIdAndReportId(userId, reportId)).isTrue();

    mockMvc
        .perform(get("/analysis/005930/reports").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reports[0].is_read").value(true))
        .andExpect(jsonPath("$.data.reports[0].applied_theme.name").value("AI/반도체"));
  }

  private void seedThemeArtifacts() throws IOException {
    Path tmp = Files.createTempDirectory("themesense-it");
    copyResource("fixtures/themesense/themes_catalog.json", tmp.resolve("themes_catalog.json"));
    copyResource(
        "fixtures/themesense/stage1_predictions.json", tmp.resolve("stage1_predictions.json"));
    copyResource(
        "fixtures/themesense/stage2_coefficients.json", tmp.resolve("stage2_coefficients.json"));
    themeIngestService.ingest(
        artifactsLoader.loadCatalog(tmp),
        artifactsLoader.loadStage1(tmp),
        artifactsLoader.loadStage2(tmp),
        STAGE1_VERSION,
        STAGE2_VERSION);
  }

  private void copyResource(String classpath, Path target) throws IOException {
    try (var in = getClass().getClassLoader().getResourceAsStream(classpath)) {
      if (in == null) {
        throw new IOException("Missing classpath resource: " + classpath);
      }
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void seedFinancials(String ticker) {
    Stock stock = stockRepository.findById(ticker).orElseThrow();
    FinancialStatement latest = new FinancialStatement(stock, PeriodType.QUARTERLY, "2024Q4");
    FinancialStatement previous = new FinancialStatement(stock, PeriodType.QUARTERLY, "2024Q3");
    setField(latest, "revenue", 1_000_000L);
    setField(latest, "netIncome", 100_000L);
    setField(latest, "totalEquity", 500_000L);
    setField(previous, "revenue", 900_000L);
    setField(previous, "netIncome", 80_000L);
    setField(previous, "totalEquity", 480_000L);
    financialStatementRepository.save(latest);
    financialStatementRepository.save(previous);
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(fieldName);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
