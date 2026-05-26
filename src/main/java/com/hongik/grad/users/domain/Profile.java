package com.hongik.grad.users.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Application-side profile keyed by the Supabase {@code auth.users.id} UUID.
 *
 * <p>The PK is supplied by Supabase at sign-up, never generated locally.
 */
@Entity
@Table(name = "profiles")
public class Profile extends BaseTimeEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(nullable = false, length = 50)
  private String nickname;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  protected Profile() {}

  public Profile(UUID id, String nickname) {
    this.id = id;
    this.nickname = nickname;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  public UUID getId() {
    return id;
  }

  public String getNickname() {
    return nickname;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
