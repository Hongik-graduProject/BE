package com.hongik.grad.disclosures.domain;

import com.hongik.grad.stocks.domain.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "disclosures")
public class Disclosure {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticker", nullable = false)
  private Stock stock;

  @Column(name = "dart_filing_id", nullable = false, length = 50, unique = true)
  private String dartFilingId;

  @Column(nullable = false, length = 255)
  private String title;

  @Convert(converter = DisclosureTypeConverter.class)
  @Column(nullable = false, length = 20)
  private DisclosureType type;

  @Column(name = "filed_at", nullable = false)
  private OffsetDateTime filedAt;

  @Column(name = "dart_url", length = 500)
  private String dartUrl;

  @Column(name = "raw_text_storage", length = 500)
  private String rawTextStorage;

  @Column(name = "text_embedding")
  private byte[] textEmbedding;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false, nullable = false)
  private OffsetDateTime createdAt;

  protected Disclosure() {}

  public Disclosure(
      Stock stock, String dartFilingId, String title, DisclosureType type, OffsetDateTime filedAt) {
    this.stock = stock;
    this.dartFilingId = dartFilingId;
    this.title = title;
    this.type = type;
    this.filedAt = filedAt;
  }

  public Long getId() {
    return id;
  }

  public Stock getStock() {
    return stock;
  }

  public String getDartFilingId() {
    return dartFilingId;
  }

  public String getTitle() {
    return title;
  }

  public DisclosureType getType() {
    return type;
  }

  public OffsetDateTime getFiledAt() {
    return filedAt;
  }

  public String getDartUrl() {
    return dartUrl;
  }

  public String getRawTextStorage() {
    return rawTextStorage;
  }

  public byte[] getTextEmbedding() {
    return textEmbedding == null ? null : textEmbedding.clone();
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
