package com.hongik.grad.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.event.ReportCreatedEvent;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.notifications.application.FcmSender;
import com.hongik.grad.notifications.domain.FcmToken;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import com.hongik.grad.notifications.domain.Notification;
import com.hongik.grad.notifications.domain.NotificationRepository;
import com.hongik.grad.notifications.domain.Platform;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeRepository;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class ReportCreatedListenerIntegrationTest {

  private static final String TICKER = "005930";

  @Autowired private StockRepository stockRepository;
  @Autowired private ProfileRepository profileRepository;
  @Autowired private FolderRepository folderRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private FcmTokenRepository fcmTokenRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ThemeRepository themeRepository;
  @Autowired private AnalysisReportRepository analysisReportRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockBean private FcmSender fcmSender;

  private UUID userA;
  private UUID userB;
  private Long reportId;
  private Long themeId;

  @BeforeEach
  void setUp() {
    transactionTemplate.executeWithoutResult(
        status -> {
          notificationRepository.deleteAll();
          analysisReportRepository.deleteAll();
          themeRepository.deleteAll();
          fcmTokenRepository.deleteAll();
          favoriteRepository.deleteAll();
          folderRepository.deleteAll();
        });

    userA = UUID.randomUUID();
    userB = UUID.randomUUID();

    transactionTemplate.executeWithoutResult(
        status -> {
          Stock stock = stockRepository.findById(TICKER).orElse(null);
          if (stock == null) {
            stock = stockRepository.save(new Stock(TICKER, "삼성전자", Market.KOSPI));
          }
          Theme theme = new Theme("AI/반도체");
          theme.setSource("naver");
          theme = themeRepository.save(theme);
          themeId = theme.getId();
          AnalysisReport report =
              analysisReportRepository.save(new AnalysisReport(stock, theme, "삼성전자 새 분석 보고서"));
          reportId = report.getId();

          profileRepository.save(new Profile(userA, "userA"));
          profileRepository.save(new Profile(userB, "userB"));

          Folder folderA = folderRepository.save(Folder.defaultFor(userA));
          Folder folderB = folderRepository.save(Folder.defaultFor(userB));
          favoriteRepository.save(new Favorite(folderA, TICKER));
          favoriteRepository.save(new Favorite(folderB, TICKER));

          fcmTokenRepository.save(new FcmToken(userA, "a1", Platform.IOS));
          fcmTokenRepository.save(new FcmToken(userA, "a2", Platform.ANDROID));
          fcmTokenRepository.save(new FcmToken(userB, "b1", Platform.IOS));
        });
  }

  @Test
  @DisplayName("ReportCreatedEvent AFTER_COMMIT → notifications 2행 + FCM 3회")
  void fanOut_afterCommit() {
    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new ReportCreatedEvent(
                    reportId, TICKER, themeId, "삼성전자 새 분석 보고서", OffsetDateTime.now())));

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .until(() -> notificationRepository.count() >= 2);

    List<Notification> all = notificationRepository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all)
        .allSatisfy(
            n -> {
              assertThat(n.getTicker()).isEqualTo(TICKER);
              assertThat(n.getReportId()).isEqualTo(reportId);
              assertThat(n.isRead()).isFalse();
            });
    assertThat(all).extracting(Notification::getUserId).containsExactlyInAnyOrder(userA, userB);

    verify(fcmSender, atLeast(3)).send(anyString(), anyString(), anyString(), any(Map.class));
  }
}
