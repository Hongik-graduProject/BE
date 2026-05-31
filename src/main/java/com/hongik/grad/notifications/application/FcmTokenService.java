package com.hongik.grad.notifications.application;

import com.hongik.grad.notifications.domain.FcmToken;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import com.hongik.grad.notifications.domain.Platform;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers and removes a user's FCM device tokens — spec §6-4 / §6-5. Both ops are idempotent. */
@Service
public class FcmTokenService {

  private final FcmTokenRepository fcmTokenRepository;

  public FcmTokenService(FcmTokenRepository fcmTokenRepository) {
    this.fcmTokenRepository = fcmTokenRepository;
  }

  /** §6-4 — UPSERT on (userId, deviceToken); refreshes platform when the token already exists. */
  @Transactional
  public void register(UUID userId, String deviceToken, String platform) {
    Platform parsed = Platform.fromValue(platform);
    Optional<FcmToken> existing =
        fcmTokenRepository.findByUserIdAndDeviceToken(userId, deviceToken);
    if (existing.isPresent()) {
      existing.get().updatePlatform(parsed);
      return;
    }
    fcmTokenRepository.save(new FcmToken(userId, deviceToken, parsed));
  }

  /** §6-5 — idempotent delete scoped to the caller; no error when the token is absent. */
  @Transactional
  public void delete(UUID userId, String deviceToken) {
    fcmTokenRepository.deleteByUserIdAndDeviceToken(userId, deviceToken);
  }
}
