package com.hongik.grad.notifications.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(
      UUID userId, boolean isRead, Pageable pageable);

  Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(
      UUID userId, NotificationType type, Pageable pageable);

  Page<Notification> findByUserIdAndIsReadAndTypeOrderByCreatedAtDesc(
      UUID userId, boolean isRead, NotificationType type, Pageable pageable);

  Optional<Notification> findByIdAndUserId(Long id, UUID userId);

  long countByUserIdAndIsReadFalse(UUID userId);

  @Modifying
  @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
  int markAllReadByUserId(@Param("userId") UUID userId);

  @Modifying
  @Query(
      "UPDATE Notification n SET n.isRead = true "
          + "WHERE n.userId = :userId AND n.ticker = :ticker AND n.isRead = false")
  int markAllReadByUserIdAndTicker(@Param("userId") UUID userId, @Param("ticker") String ticker);
}
