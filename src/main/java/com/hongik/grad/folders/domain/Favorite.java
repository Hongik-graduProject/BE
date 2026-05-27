package com.hongik.grad.folders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "favorites")
public class Favorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "folder_id", nullable = false)
  private Folder folder;

  @Column(nullable = false, length = 10)
  private String ticker;

  @Column(length = 100)
  private String memo;

  @Column(name = "avg_price")
  private Long avgPrice;

  @CreationTimestamp
  @Column(name = "added_at", nullable = false, updatable = false)
  private OffsetDateTime addedAt;

  protected Favorite() {}

  public Favorite(Folder folder, String ticker) {
    this.folder = folder;
    this.ticker = ticker;
  }

  public void updateMemo(String memo) {
    this.memo = memo;
  }

  public void updateAvgPrice(Long avgPrice) {
    this.avgPrice = avgPrice;
  }

  public void moveToFolder(Folder target) {
    this.folder = target;
  }

  public Long getId() {
    return id;
  }

  public Folder getFolder() {
    return folder;
  }

  public String getTicker() {
    return ticker;
  }

  public String getMemo() {
    return memo;
  }

  public Long getAvgPrice() {
    return avgPrice;
  }

  public OffsetDateTime getAddedAt() {
    return addedAt;
  }
}
