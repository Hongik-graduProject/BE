package com.hongik.grad.notifications.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A user's FCM device token. UNIQUE(user_id, device_token); has created_at + updated_at. */
@Entity
@Table(name = "fcm_tokens")
public class FcmToken extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
  private UUID userId;

  @Column(name = "device_token", nullable = false, length = 512)
  private String deviceToken;

  @Convert(converter = Platform.JpaConverter.class)
  @Column(nullable = false, length = 10)
  private Platform platform;

  protected FcmToken() {}

  public FcmToken(UUID userId, String deviceToken, Platform platform) {
    this.userId = userId;
    this.deviceToken = deviceToken;
    this.platform = platform;
  }

  public void updatePlatform(Platform platform) {
    this.platform = platform;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getDeviceToken() {
    return deviceToken;
  }

  public Platform getPlatform() {
    return platform;
  }
}
