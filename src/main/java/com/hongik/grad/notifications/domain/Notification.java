package com.hongik.grad.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Per-user notification row. The V1 schema has only {@code created_at} (no {@code updated_at}), so
 * this entity uses a standalone {@link CreationTimestamp} rather than extending {@code
 * BaseTimeEntity}.
 */
@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
  private UUID userId;

  @Convert(converter = NotificationType.JpaConverter.class)
  @Column(nullable = false, length = 30)
  private NotificationType type;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "text")
  private String body;

  @Column(length = 10)
  private String ticker;

  @Column(name = "report_id")
  private Long reportId;

  @Column(name = "disclosure_id")
  private Long disclosureId;

  @Column(name = "is_read", nullable = false)
  private boolean isRead;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false, nullable = false)
  private OffsetDateTime createdAt;

  protected Notification() {}

  public Notification(
      UUID userId, NotificationType type, String title, String body, String ticker, Long reportId) {
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.ticker = ticker;
    this.reportId = reportId;
    this.isRead = false;
  }

  /**
   * New-disclosure notification (analysis-independent). Sets {@code disclosure_id} only; {@code
   * report_id} stays null — the two links are mutually exclusive.
   */
  public static Notification forDisclosure(
      UUID userId, String title, String body, String ticker, Long disclosureId) {
    Notification n = new Notification();
    n.userId = userId;
    n.type = NotificationType.NEW_DISCLOSURE;
    n.title = title;
    n.body = body;
    n.ticker = ticker;
    n.disclosureId = disclosureId;
    n.isRead = false;
    return n;
  }

  public void markRead() {
    this.isRead = true;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getTicker() {
    return ticker;
  }

  public Long getReportId() {
    return reportId;
  }

  public Long getDisclosureId() {
    return disclosureId;
  }

  public boolean isRead() {
    return isRead;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
