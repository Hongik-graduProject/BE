package com.hongik.grad.themes.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
import com.hongik.grad.stocks.domain.Stock;
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

@Entity
@Table(name = "stock_themes")
public class StockTheme extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ticker", nullable = false)
  private Stock stock;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "theme_id", nullable = false)
  private Theme theme;

  @Column(name = "probability")
  private double probability;

  @Column(name = "rank", nullable = false)
  private short rank;

  @Column(name = "is_primary")
  private boolean isPrimary;

  @Column(name = "model_version", length = 50)
  private String modelVersion;

  @Column(name = "predicted_at")
  private OffsetDateTime predictedAt;

  protected StockTheme() {}

  public StockTheme(Stock stock, Theme theme, short rank) {
    this.stock = stock;
    this.theme = theme;
    this.rank = rank;
  }

  public Long getId() {
    return id;
  }

  public Stock getStock() {
    return stock;
  }

  public Theme getTheme() {
    return theme;
  }

  public double getProbability() {
    return probability;
  }

  public short getRank() {
    return rank;
  }

  public boolean isPrimary() {
    return isPrimary;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public OffsetDateTime getPredictedAt() {
    return predictedAt;
  }

  public void setRank(short rank) {
    this.rank = rank;
  }

  public void setProbability(double probability) {
    this.probability = probability;
  }

  public void setPrimary(boolean primary) {
    this.isPrimary = primary;
  }

  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  public void setPredictedAt(OffsetDateTime predictedAt) {
    this.predictedAt = predictedAt;
  }
}
