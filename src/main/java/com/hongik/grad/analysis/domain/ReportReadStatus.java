package com.hongik.grad.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "report_read_status")
public class ReportReadStatus {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(name = "report_id", nullable = false)
  private Long reportId;

  @CreationTimestamp
  @Column(name = "read_at", updatable = false, nullable = false)
  private OffsetDateTime readAt;

  protected ReportReadStatus() {}

  public ReportReadStatus(UUID userId, Long reportId) {
    this.userId = userId;
    this.reportId = reportId;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getReportId() {
    return reportId;
  }

  public OffsetDateTime getReadAt() {
    return readAt;
  }
}
