package com.hongik.grad.folders.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * User-owned folder. PR-03 introduces the entity so onboarding can create the per-user default
 * folder atomically; the REST surface (controller + service) ships in PR-04.
 */
@Entity
@Table(name = "folders")
public class Folder extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 30)
  private String name;

  @Column(length = 7, nullable = false)
  private String color;

  @Convert(converter = FolderType.JpaConverter.class)
  @Column(nullable = false, length = 10)
  private FolderType type;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  protected Folder() {}

  private Folder(UUID userId, String name, String color, FolderType type, int displayOrder) {
    this.userId = userId;
    this.name = name;
    this.color = color;
    this.type = type;
    this.displayOrder = displayOrder;
  }

  /** Per-user default folder created during onboarding. */
  public static Folder defaultFor(UUID userId) {
    return new Folder(userId, "기본 폴더", "#999999", FolderType.DEFAULT, 0);
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getColor() {
    return color;
  }

  public FolderType getType() {
    return type;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }
}
