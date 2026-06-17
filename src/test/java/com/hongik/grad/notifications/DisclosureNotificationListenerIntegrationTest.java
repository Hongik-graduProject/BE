package com.hongik.grad.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import com.hongik.grad.analysis.application.AnalysisReportGenerator;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.notifications.application.FcmSender;
import com.hongik.grad.notifications.domain.FcmToken;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import com.hongik.grad.notifications.domain.Notification;
import com.hongik.grad.notifications.domain.NotificationRepository;
import com.hongik.grad.notifications.domain.NotificationType;
import com.hongik.grad.notifications.domain.Platform;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
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
class DisclosureNotificationListenerIntegrationTest {

  private static final String TICKER = "005930";

  @Autowired private StockRepository stockRepository;
  @Autowired private ProfileRepository profileRepository;
  @Autowired private FolderRepository folderRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private FcmTokenRepository fcmTokenRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private DisclosureRepository disclosureRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockBean private FcmSender fcmSender;

  // Same event triggers report generation too; stub it out to isolate the notification path.
  @MockBean private AnalysisReportGenerator analysisReportGenerator;

  private UUID userA;
  private UUID userB;
  private Long disclosureId;

  @BeforeEach
  void setUp() {
    transactionTemplate.executeWithoutResult(
        status -> {
          notificationRepository.deleteAll();
          disclosureRepository.deleteAll();
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
          Disclosure disclosure =
              disclosureRepository.save(
                  new Disclosure(
                      stock,
                      "dart-" + UUID.randomUUID(),
                      "유상증자 결정",
                      DisclosureType.EVENT,
                      OffsetDateTime.now()));
          disclosureId = disclosure.getId();

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
  @DisplayName("DisclosureCreatedEvent AFTER_COMMIT → new_disclosure 알림 2행(disclosureId) + FCM 3회")
  void fanOut_afterCommit() {
    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new DisclosureCreatedEvent(
                    disclosureId,
                    TICKER,
                    "dart-filing",
                    "유상증자 결정",
                    DisclosureType.EVENT,
                    OffsetDateTime.now())));

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .until(() -> notificationRepository.count() >= 2);

    List<Notification> all = notificationRepository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all)
        .allSatisfy(
            n -> {
              assertThat(n.getType()).isEqualTo(NotificationType.NEW_DISCLOSURE);
              assertThat(n.getTicker()).isEqualTo(TICKER);
              assertThat(n.getDisclosureId()).isEqualTo(disclosureId);
              assertThat(n.getReportId()).isNull();
              assertThat(n.getTitle()).isEqualTo("[삼성전자] 새 공시");
              assertThat(n.getBody()).isEqualTo("유상증자 결정");
              assertThat(n.isRead()).isFalse();
            });
    assertThat(all).extracting(Notification::getUserId).containsExactlyInAnyOrder(userA, userB);

    verify(fcmSender, atLeast(3)).send(anyString(), anyString(), anyString(), any(Map.class));
  }
}
