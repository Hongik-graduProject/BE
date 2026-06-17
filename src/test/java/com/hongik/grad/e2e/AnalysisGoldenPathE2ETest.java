package com.hongik.grad.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.disclosures.application.DartPoller;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.notifications.application.FcmSender;
import com.hongik.grad.notifications.domain.FcmToken;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import com.hongik.grad.notifications.domain.NotificationRepository;
import com.hongik.grad.notifications.domain.NotificationType;
import com.hongik.grad.notifications.domain.Platform;
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
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR-10 core golden path E2E — true end-to-end from the DART HTTP boundary through to the user
 * reading the generated report.
 *
 * <p>Unlike {@code AnalysisGoldenPathIntegrationTest} (PR-08), which INSERTs a {@code Disclosure}
 * directly and publishes the {@code DisclosureCreatedEvent} by hand, this test enters through the
 * real {@link DartPoller}: a stubbed DART {@code /api/list.json} response (served by {@link
 * MockWebServer}) is fetched by {@code DartClient}, persisted, and propagated through the full
 * committed-event chain:
 *
 * <pre>
 *   DartPoller.poll()
 *     → DartClient (MockWebServer)
 *     → DisclosureService.upsertBatch → Disclosure INSERT + DisclosureCreatedEvent
 *     → DisclosureCreatedListener (AFTER_COMMIT) → AnalysisReportGenerator → AnalysisReport + ReportCreatedEvent
 *     → ReportCreatedListener (AFTER_COMMIT) → notifications INSERT + FcmSender.send
 *     → GET /analysis/reports/{id} (is_read flips true)
 * </pre>
 *
 * No new production code is added — only existing entry points are wired together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class AnalysisGoldenPathE2ETest {

  private static final String TICKER = "005930";
  private static final String STAGE1_VERSION = "themesense-stage1-v1.0";
  private static final String STAGE2_VERSION = "themesense-stage2-v1.0";

  static MockWebServer mockWebServer;

  @Autowired private MockMvc mockMvc;
  @Autowired private DartPoller dartPoller;
  @Autowired private ArtifactsLoader artifactsLoader;
  @Autowired private ThemeIngestService themeIngestService;
  @Autowired private StockRepository stockRepository;
  @Autowired private FinancialStatementRepository financialStatementRepository;
  @Autowired private DisclosureRepository disclosureRepository;
  @Autowired private AnalysisReportRepository analysisReportRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private FcmTokenRepository fcmTokenRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private FolderRepository folderRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockBean private FcmSender fcmSender;

  @PersistenceContext private EntityManager em;

  private UUID userId;
  private String token;

  @DynamicPropertySource
  static void dartProperties(DynamicPropertyRegistry registry) {
    try {
      mockWebServer = new MockWebServer();
      mockWebServer.start();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    registry.add("dart.api.key", () -> "test-api-key");
    registry.add("dart.api.base-url", () -> mockWebServer.url("/").toString());
  }

  @AfterAll
  static void shutdown() throws IOException {
    if (mockWebServer != null) {
      mockWebServer.shutdown();
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    transactionTemplate.executeWithoutResult(
        status -> {
          notificationRepository.deleteAll();
          analysisReportRepository.deleteAll();
          disclosureRepository.deleteAll();
          fcmTokenRepository.deleteAll();
          favoriteRepository.deleteAll();
          folderRepository.deleteAll();
        });

    transactionTemplate.executeWithoutResult(
        status -> {
          if (!stockRepository.existsById(TICKER)) {
            stockRepository.save(new Stock(TICKER, "삼성전자", Market.KOSPI));
          }
        });

    seedThemeArtifacts();
    transactionTemplate.executeWithoutResult(status -> seedFinancials(TICKER));

    userId = UUID.randomUUID();
    UUID finalUserId = userId;
    transactionTemplate.executeWithoutResult(
        status -> {
          em.createNativeQuery("INSERT INTO profiles (id, nickname) VALUES (:id, :nickname)")
              .setParameter("id", finalUserId)
              .setParameter("nickname", "tester")
              .executeUpdate();
          Folder folder = folderRepository.save(Folder.defaultFor(finalUserId));
          favoriteRepository.save(new Favorite(folder, TICKER));
          fcmTokenRepository.save(new FcmToken(finalUserId, "device-token-1", Platform.IOS));
        });

    token = "Bearer " + JwtTestFactory.validToken(userId, "tester@example.com");
  }

  @Test
  @DisplayName("DART stub → poll → 보고서 자동 생성 → FCM 발송 + 알림 INSERT → GET 상세(is_read=true)")
  void analysisGoldenPathFromDart() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(dartListResponse()));

    // Entry point: real poll → DartClient hits MockWebServer.
    dartPoller.poll();

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .until(() -> !disclosureRepository.findAll().isEmpty());

    // AFTER_COMMIT chain: DisclosureCreatedEvent → AnalysisReport.
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .until(() -> !analysisReportRepository.findAll().isEmpty());

    // Two AFTER_COMMIT fan-outs land for this disclosure: the new-disclosure notification
    // (DisclosureCreatedEvent) and the report notification (ReportCreatedEvent).
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .until(() -> notificationRepository.count() >= 2);

    Long reportId =
        transactionTemplate.execute(
            status -> {
              AnalysisReport report = analysisReportRepository.findAll().get(0);
              assertThat(report.getStock().getTicker()).isEqualTo(TICKER);
              assertThat(report.getAppliedTheme().getName()).isEqualTo("AI/반도체");
              return report.getId();
            });

    // Both notifications belong to the favoriting user and are unread: the report notification
    // (report_id set, disclosure_id null) coexists with the new-disclosure notification
    // (disclosure_id set, report_id null).
    assertThat(notificationRepository.findAll())
        .hasSize(2)
        .allSatisfy(
            n -> {
              assertThat(n.getUserId()).isEqualTo(userId);
              assertThat(n.getTicker()).isEqualTo(TICKER);
              assertThat(n.isRead()).isFalse();
            });
    assertThat(notificationRepository.findAll())
        .anySatisfy(
            n -> {
              assertThat(n.getType()).isEqualTo(NotificationType.DISCLOSURE_REPORT);
              assertThat(n.getReportId()).isEqualTo(reportId);
              assertThat(n.getDisclosureId()).isNull();
            })
        .anySatisfy(
            n -> {
              assertThat(n.getType()).isEqualTo(NotificationType.NEW_DISCLOSURE);
              assertThat(n.getDisclosureId()).isNotNull();
              assertThat(n.getReportId()).isNull();
            });

    verify(fcmSender, atLeastOnce()).send(anyString(), anyString(), anyString(), any(Map.class));

    // User reads the report — is_read flips true and the §5-3 payload keys are present.
    mockMvc
        .perform(get("/analysis/reports/" + reportId).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.id").value(reportId.intValue()))
        .andExpect(jsonPath("$.data.ticker").value(TICKER))
        .andExpect(jsonPath("$.data.name").value("삼성전자"))
        .andExpect(jsonPath("$.data.applied_theme.name").value("AI/반도체"))
        .andExpect(jsonPath("$.data.metric_contributions").isArray())
        .andExpect(jsonPath("$.data.is_read").value(true));

    // Notification list returns both fan-out entries (report + new-disclosure) with the §6-1
    // unread_count key; report_id is asserted order-independently.
    mockMvc
        .perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unread_count").isNumber())
        .andExpect(jsonPath("$.data.notifications.length()").value(2))
        .andExpect(jsonPath("$.data.notifications[*].ticker", everyItem(equalTo(TICKER))))
        .andExpect(jsonPath("$.data.notifications[*].report_id", hasItem(reportId.intValue())));
  }

  private String dartListResponse() {
    return """
        {
          "status": "000",
          "message": "정상",
          "page_no": 1,
          "page_count": 100,
          "total_count": 1,
          "total_page": 1,
          "list": [
            {
              "corp_code": "00126380",
              "corp_name": "삼성전자",
              "stock_code": "005930",
              "report_nm": "분기보고서 (2025.03)",
              "rcept_no": "20250401000099",
              "flr_nm": "삼성전자",
              "rcept_dt": "20250401",
              "rm": ""
            }
          ]
        }
        """;
  }

  private void seedThemeArtifacts() throws IOException {
    Path tmp = Files.createTempDirectory("themesense-e2e");
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
