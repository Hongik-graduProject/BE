package com.hongik.grad.stocks.domain;

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
@Table(name = "stock_news")
public class StockNews {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticker", nullable = false)
  private Stock stock;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(length = 100)
  private String source;

  @Column(nullable = false, length = 500, unique = true)
  private String url;

  @Column(name = "thumbnail_url", length = 500)
  private String thumbnailUrl;

  @Column(name = "published_at", nullable = false)
  private OffsetDateTime publishedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected StockNews() {}

  public StockNews(
      Stock stock,
      String title,
      String source,
      String url,
      String thumbnailUrl,
      OffsetDateTime publishedAt) {
    this.stock = stock;
    this.title = title;
    this.source = source;
    this.url = url;
    this.thumbnailUrl = thumbnailUrl;
    this.publishedAt = publishedAt;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getSource() {
    return source;
  }

  public String getUrl() {
    return url;
  }

  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }
}
