package com.hongik.grad.themes.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
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

@Entity
@Table(name = "theme_metric_sensitivity")
public class ThemeMetricSensitivity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "theme_id", nullable = false)
  private Theme theme;

  @Column(name = "metric_code", nullable = false, length = 20)
  private String metricCode;

  @Column(nullable = false)
  private double beta;

  @Column(name = "abs_beta", nullable = false)
  private double absBeta;

  @Column(nullable = false)
  private int rank;

  @Convert(converter = DirectionConverter.class)
  @Column(length = 10)
  private Direction direction;

  @Column(name = "r_squared")
  private Double rSquared;

  @Column(name = "sample_size")
  private Integer sampleSize;

  @Column(name = "model_version", length = 50)
  private String modelVersion;

  @Column(name = "computed_at")
  private OffsetDateTime computedAt;

  protected ThemeMetricSensitivity() {}

  public ThemeMetricSensitivity(Theme theme, String metricCode, double beta, double absBeta, int rank) {
    this.theme = theme;
    this.metricCode = metricCode;
    this.beta = beta;
    this.absBeta = absBeta;
    this.rank = rank;
  }

  public Long getId() {
    return id;
  }

  public Theme getTheme() {
    return theme;
  }

  public String getMetricCode() {
    return metricCode;
  }

  public double getBeta() {
    return beta;
  }

  public double getAbsBeta() {
    return absBeta;
  }

  public int getRank() {
    return rank;
  }

  public Direction getDirection() {
    return direction;
  }

  public Double getRSquared() {
    return rSquared;
  }

  public Integer getSampleSize() {
    return sampleSize;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public OffsetDateTime getComputedAt() {
    return computedAt;
  }

  public void setBeta(double beta) {
    this.beta = beta;
  }

  public void setAbsBeta(double absBeta) {
    this.absBeta = absBeta;
  }

  public void setRank(int rank) {
    this.rank = rank;
  }

  public void setDirection(Direction direction) {
    this.direction = direction;
  }

  public void setRSquared(Double rSquared) {
    this.rSquared = rSquared;
  }

  public void setSampleSize(Integer sampleSize) {
    this.sampleSize = sampleSize;
  }

  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  public void setComputedAt(OffsetDateTime computedAt) {
    this.computedAt = computedAt;
  }
}
