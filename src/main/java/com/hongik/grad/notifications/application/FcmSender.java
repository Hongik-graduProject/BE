package com.hongik.grad.notifications.application;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends a single FCM push per device token on a background thread. When the {@link
 * FirebaseMessaging} bean is absent (no credentials), this is a no-op. Tokens rejected as
 * unregistered/invalid are pruned from {@code fcm_tokens}; other failures are logged and ignored so
 * one bad token never blocks the rest.
 */
@Component
public class FcmSender {

  private static final Logger log = LoggerFactory.getLogger(FcmSender.class);

  private final ObjectProvider<FirebaseMessaging> firebaseMessaging;
  private final FcmTokenRepository fcmTokenRepository;

  public FcmSender(
      ObjectProvider<FirebaseMessaging> firebaseMessaging, FcmTokenRepository fcmTokenRepository) {
    this.firebaseMessaging = firebaseMessaging;
    this.fcmTokenRepository = fcmTokenRepository;
  }

  @Async("taskExecutor")
  public void send(String deviceToken, String title, String body, Map<String, String> data) {
    FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
    if (messaging == null) {
      log.warn("FCM disabled — skipping push to token (suffix={}).", tokenSuffix(deviceToken));
      return;
    }
    Message message =
        Message.builder()
            .setToken(deviceToken)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putAllData(data == null ? Map.of() : data)
            .build();
    try {
      String messageId = messaging.send(message);
      log.debug("FCM push sent message_id={} token_suffix={}", messageId, tokenSuffix(deviceToken));
    } catch (FirebaseMessagingException ex) {
      if (isInvalidToken(ex)) {
        log.info(
            "Pruning invalid FCM token (suffix={}, error={}).",
            tokenSuffix(deviceToken),
            ex.getMessagingErrorCode());
        fcmTokenRepository.deleteByDeviceToken(deviceToken);
      } else {
        log.warn(
            "FCM push failed (suffix={}, error={}): {}",
            tokenSuffix(deviceToken),
            ex.getMessagingErrorCode(),
            ex.getMessage());
      }
    } catch (RuntimeException ex) {
      log.warn(
          "Unexpected FCM push failure (suffix={}): {}", tokenSuffix(deviceToken), ex.getMessage());
    }
  }

  private boolean isInvalidToken(FirebaseMessagingException ex) {
    MessagingErrorCode code = ex.getMessagingErrorCode();
    return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
  }

  private String tokenSuffix(String deviceToken) {
    if (deviceToken == null || deviceToken.length() <= 6) {
      return "******";
    }
    return deviceToken.substring(deviceToken.length() - 6);
  }
}
