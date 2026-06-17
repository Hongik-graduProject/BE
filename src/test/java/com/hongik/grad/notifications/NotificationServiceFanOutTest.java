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
import com.hongik.grad.notifications.domain.NotificationType;
import com.hongik.grad.notifications.domain.Platform;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
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

  @Test
  @DisplayName("새 공시 fanout: 2 user × 디바이스 → new_disclosure 알림 2행(disclosureId 채움) + FCM 호출")
  void fanOutDisclosure_twoUsers() {
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    List<UUID> userIds = List.of(userA, userB);
    given(favoriteRepository.findDistinctUserIdsByTicker("005930")).willReturn(userIds);
    given(stockRepository.findById("005930"))
        .willReturn(java.util.Optional.of(new Stock("005930", "삼성전자", Market.KOSPI)));
    given(fcmTokenRepository.findByUserIdIn(userIds))
        .willReturn(
            List.of(
                new FcmToken(userA, "a1", Platform.IOS),
                new FcmToken(userB, "b1", Platform.ANDROID),
                new FcmToken(userB, "b2", Platform.IOS)));

    service().fanOutDisclosureCreated(777L, "005930", "유상증자 결정");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Notification>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(notificationRepository).saveAll(savedCaptor.capture());
    List<Notification> saved = savedCaptor.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved)
        .allSatisfy(
            n -> {
              assertThat(n.getType()).isEqualTo(NotificationType.NEW_DISCLOSURE);
              assertThat(n.getDisclosureId()).isEqualTo(777L);
              assertThat(n.getReportId()).isNull();
              assertThat(n.getTicker()).isEqualTo("005930");
              assertThat(n.getTitle()).isEqualTo("[삼성전자] 새 공시");
              assertThat(n.getBody()).isEqualTo("유상증자 결정");
            });
    verify(fcmSender, times(3)).send(anyString(), eq("[삼성전자] 새 공시"), eq("유상증자 결정"), any(Map.class));
  }

  @Test
  @DisplayName("새 공시 fanout: 종목명 없으면 ticker로 title 구성")
  void fanOutDisclosure_unknownStock_usesTicker() {
    UUID userA = UUID.randomUUID();
    List<UUID> userIds = List.of(userA);
    given(favoriteRepository.findDistinctUserIdsByTicker("999999")).willReturn(userIds);
    given(stockRepository.findById("999999")).willReturn(java.util.Optional.empty());
    given(fcmTokenRepository.findByUserIdIn(userIds))
        .willReturn(List.of(new FcmToken(userA, "a1", Platform.IOS)));

    service().fanOutDisclosureCreated(1L, "999999", "공시 제목");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Notification>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(notificationRepository).saveAll(savedCaptor.capture());
    assertThat(savedCaptor.getValue().get(0).getTitle()).isEqualTo("[999999] 새 공시");
  }

  @Test
  @DisplayName("새 공시 fanout: 즐겨찾기 user 없음 → noop")
  void fanOutDisclosure_noFavorites_noop() {
    given(favoriteRepository.findDistinctUserIdsByTicker("000000")).willReturn(List.of());

    service().fanOutDisclosureCreated(1L, "000000", "title");

    verify(notificationRepository, never()).saveAll(anyList());
    verify(fcmSender, never()).send(anyString(), anyString(), anyString(), any());
  }
}
