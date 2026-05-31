package com.hongik.grad.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.notifications.application.FcmSender;
import com.hongik.grad.notifications.application.NotificationService;
import com.hongik.grad.notifications.domain.FcmToken;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import com.hongik.grad.notifications.domain.Notification;
import com.hongik.grad.notifications.domain.NotificationRepository;
import com.hongik.grad.notifications.domain.Platform;
import com.hongik.grad.stocks.domain.StockRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceFanOutTest {

  @Mock private FavoriteRepository favoriteRepository;
  @Mock private NotificationRepository notificationRepository;
  @Mock private FcmTokenRepository fcmTokenRepository;
  @Mock private StockRepository stockRepository;
  @Mock private FcmSender fcmSender;

  private NotificationService service() {
    return new NotificationService(
        favoriteRepository, notificationRepository, fcmTokenRepository, stockRepository, fcmSender);
  }

  @Test
  @DisplayName("2 user × 3 device → notifications 2행 INSERT + FCM 6회 호출")
  void fanOut_twoUsersThreeDevices() {
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    List<UUID> userIds = List.of(userA, userB);
    given(favoriteRepository.findDistinctUserIdsByTicker("005930")).willReturn(userIds);
    given(fcmTokenRepository.findByUserIdIn(userIds))
        .willReturn(
            List.of(
                new FcmToken(userA, "a1", Platform.IOS),
                new FcmToken(userA, "a2", Platform.ANDROID),
                new FcmToken(userA, "a3", Platform.IOS),
                new FcmToken(userB, "b1", Platform.IOS),
                new FcmToken(userB, "b2", Platform.ANDROID),
                new FcmToken(userB, "b3", Platform.IOS)));

    service().fanOutReportCreated(501L, "005930", "삼성전자 새 분석 보고서");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Notification>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(notificationRepository).saveAll(savedCaptor.capture());
    assertThat(savedCaptor.getValue()).hasSize(2);
    verify(fcmSender, times(6)).send(anyString(), eq("삼성전자 새 분석 보고서"), anyString(), any(Map.class));
  }

  @Test
  @DisplayName("즐겨찾기 user 없음 → notification/FCM 모두 호출 안 함")
  void fanOut_noFavorites_noop() {
    given(favoriteRepository.findDistinctUserIdsByTicker("000000")).willReturn(List.of());

    service().fanOutReportCreated(1L, "000000", "title");

    verify(notificationRepository, never()).saveAll(anyList());
    verify(fcmSender, never()).send(anyString(), anyString(), anyString(), any());
  }
}
