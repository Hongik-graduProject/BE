package com.hongik.grad.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.hongik.grad.notifications.application.FcmSender;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class FcmSenderTest {

  @Mock private FirebaseMessaging firebaseMessaging;
  @Mock private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
  @Mock private FcmTokenRepository fcmTokenRepository;

  private FcmSender newSender() {
    return new FcmSender(firebaseMessagingProvider, fcmTokenRepository);
  }

  @Test
  @DisplayName("유효 토큰 → FirebaseMessaging.send 호출, 토큰 삭제 없음")
  void send_validToken_callsSend() throws Exception {
    given(firebaseMessagingProvider.getIfAvailable()).willReturn(firebaseMessaging);
    given(firebaseMessaging.send(any(Message.class))).willReturn("msg-1");

    newSender().send("validToken", "title", "body", Map.of("k", "v"));

    verify(firebaseMessaging).send(any(Message.class));
    verify(fcmTokenRepository, never()).deleteByDeviceToken(any());
  }

  @Test
  @DisplayName("UNREGISTERED 예외 → 해당 토큰 삭제")
  void send_unregistered_prunesToken() throws Exception {
    given(firebaseMessagingProvider.getIfAvailable()).willReturn(firebaseMessaging);
    FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
    given(ex.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
    given(firebaseMessaging.send(any(Message.class))).willThrow(ex);

    newSender().send("staleToken", "title", "body", Map.of());

    verify(fcmTokenRepository).deleteByDeviceToken("staleToken");
  }

  @Test
  @DisplayName("그 외 예외(UNAVAILABLE) → 토큰 유지(삭제 안 함)")
  void send_otherError_keepsToken() throws Exception {
    given(firebaseMessagingProvider.getIfAvailable()).willReturn(firebaseMessaging);
    FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
    given(ex.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
    given(firebaseMessaging.send(any(Message.class))).willThrow(ex);

    newSender().send("token", "title", "body", Map.of());

    verify(fcmTokenRepository, never()).deleteByDeviceToken(any());
  }

  @Test
  @DisplayName("FirebaseMessaging bean 없음 → no-op (예외 없음)")
  void send_noFirebase_noOp() {
    given(firebaseMessagingProvider.getIfAvailable()).willReturn(null);

    newSender().send("token", "title", "body", Map.of());

    verify(fcmTokenRepository, never()).deleteByDeviceToken(any());
  }
}
