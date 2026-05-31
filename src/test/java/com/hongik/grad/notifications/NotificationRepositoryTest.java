package com.hongik.grad.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hongik.grad.notifications.domain.FcmToken;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import com.hongik.grad.notifications.domain.Notification;
import com.hongik.grad.notifications.domain.NotificationRepository;
import com.hongik.grad.notifications.domain.NotificationType;
import com.hongik.grad.notifications.domain.Platform;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class NotificationRepositoryTest {

  @Autowired private NotificationRepository notificationRepository;
  @Autowired private FcmTokenRepository fcmTokenRepository;
  @Autowired private ProfileRepository profileRepository;
  @Autowired private TestEntityManager em;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    profileRepository.save(new Profile(userId, "테스터"));
    em.flush();
    em.clear();
  }

  @Test
  @DisplayName("Notification 저장 시 id/createdAt 채워지고 type lowercase 매핑")
  void saveNotification() {
    Notification saved =
        notificationRepository.save(
            new Notification(
                userId, NotificationType.DISCLOSURE_REPORT, "삼성전자 보고서", "본문", null, null));
    em.flush();
    em.clear();

    Notification found = notificationRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getType()).isEqualTo(NotificationType.DISCLOSURE_REPORT);
    assertThat(found.isRead()).isFalse();
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getTitle()).isEqualTo("삼성전자 보고서");
  }

  @Test
  @DisplayName("countByUserIdAndIsReadFalse 는 미확인 알림 수를 센다")
  void unreadCount() {
    notificationRepository.save(
        new Notification(userId, NotificationType.DISCLOSURE_REPORT, "a", null, null, null));
    notificationRepository.save(
        new Notification(userId, NotificationType.DISCLOSURE_REPORT, "b", null, null, null));
    em.flush();
    em.clear();

    assertThat(notificationRepository.countByUserIdAndIsReadFalse(userId)).isEqualTo(2);
  }

  @Test
  @DisplayName("FcmToken UNIQUE(user_id, device_token) 위반 시 예외")
  void fcmTokenUniqueViolation() {
    fcmTokenRepository.save(new FcmToken(userId, "tokenA", Platform.IOS));
    em.flush();

    assertThatThrownBy(
            () -> {
              fcmTokenRepository.save(new FcmToken(userId, "tokenA", Platform.ANDROID));
              em.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("FcmToken platform lowercase 매핑 + findByUserId 조회")
  void fcmTokenPlatformMapping() {
    fcmTokenRepository.save(new FcmToken(userId, "tokenB", Platform.ANDROID));
    em.flush();
    em.clear();

    assertThat(fcmTokenRepository.findByUserId(userId))
        .singleElement()
        .satisfies(t -> assertThat(t.getPlatform()).isEqualTo(Platform.ANDROID));
  }
}
