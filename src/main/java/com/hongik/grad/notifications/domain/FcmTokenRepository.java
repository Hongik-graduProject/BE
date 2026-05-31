package com.hongik.grad.notifications.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

  List<FcmToken> findByUserId(UUID userId);

  List<FcmToken> findByUserIdIn(Collection<UUID> userIds);

  Optional<FcmToken> findByUserIdAndDeviceToken(UUID userId, String deviceToken);

  void deleteByUserIdAndDeviceToken(UUID userId, String deviceToken);

  void deleteByDeviceToken(String deviceToken);
}
