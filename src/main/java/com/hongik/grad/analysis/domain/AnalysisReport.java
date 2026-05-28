package com.hongik.grad.analysis.domain;

import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.analysis.dto.ReportMetrics;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.themes.domain.Theme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_reports")
public class AnalysisReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticker", nullable = false)
  private Stock stock;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disclosure_id")
  private Disclosure disclosure;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applied_theme_id", nullable = false)
  private Theme appliedTheme;

  @Column(name = "theme_routing_score", precision = 5, scale = 4)
  private BigDecimal themeRoutingScore;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(name = "expected_car", precision = 7, scale = 4)
  private BigDecimal expectedCar;

  @Column(name = "car_lower", precision = 7, scale = 4)
  private BigDecimal carLower;

  @Column(name = "car_upper", precision = 7, scale = 4)
  private BigDecimal carUpper;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metric_contributions", columnDefinition = "jsonb")
  private List<MetricContribution> metricContributions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "key_findings", columnDefinition = "jsonb")
  private List<String> keyFindings;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metrics", columnDefinition = "jsonb")
  private ReportMetrics metrics;

  @Column(name = "stage1_model_version", length = 50)
  private String stage1ModelVersion;

  @Column(name = "stage2_model_version", length = 50)
  private String stage2ModelVersion;

  @CreationTimestamp
  @Column(name = "generated_at", updatable = false, nullable = false)
  private OffsetDateTime generatedAt;

  protected AnalysisReport() {}

  public AnalysisReport(Stock stock, Theme appliedTheme, String title) {
    this.stock = stock;
    this.appliedTheme = appliedTheme;
    this.title = title;
  }

  public Long getId() {
    return id;
  }

  public Stock getStock() {
    return stock;
  }

  public Disclosure getDisclosure() {
    return disclosure;
  }

  public Theme getAppliedTheme() {
    return appliedTheme;
  }

  public BigDecimal getThemeRoutingScore() {
    return themeRoutingScore;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public BigDecimal getExpectedCar() {
    return expectedCar;
  }

  public BigDecimal getCarLower() {
    return carLower;
  }

  public BigDecimal getCarUpper() {
    return carUpper;
  }

  public List<MetricContribution> getMetricContributions() {
    return metricContributions;
  }

  public List<String> getKeyFindings() {
    return keyFindings;
  }

  public ReportMetrics getMetrics() {
    return metrics;
  }

  public String getStage1ModelVersion() {
    return stage1ModelVersion;
  }

  public String getStage2ModelVersion() {
    return stage2ModelVersion;
  }

  public OffsetDateTime getGeneratedAt() {
    return generatedAt;
  }

  public void setDisclosure(Disclosure disclosure) {
    this.disclosure = disclosure;
  }

  public void setThemeRoutingScore(BigDecimal themeRoutingScore) {
    this.themeRoutingScore = themeRoutingScore;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public void setExpectedCar(BigDecimal expectedCar) {
    this.expectedCar = expectedCar;
  }

  public void setCarLower(BigDecimal carLower) {
    this.carLower = carLower;
  }

  public void setCarUpper(BigDecimal carUpper) {
    this.carUpper = carUpper;
  }

  public void setMetricContributions(List<MetricContribution> metricContributions) {
    this.metricContributions = metricContributions;
  }

  public void setKeyFindings(List<String> keyFindings) {
    this.keyFindings = keyFindings;
  }

  public void setMetrics(ReportMetrics metrics) {
    this.metrics = metrics;
  }

  public void setStage1ModelVersion(String stage1ModelVersion) {
    this.stage1ModelVersion = stage1ModelVersion;
  }

  public void setStage2ModelVersion(String stage2ModelVersion) {
    this.stage2ModelVersion = stage2ModelVersion;
  }
}
