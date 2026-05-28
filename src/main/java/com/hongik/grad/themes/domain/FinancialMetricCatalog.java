package com.hongik.grad.themes.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_metric_catalog")
public class FinancialMetricCatalog extends BaseTimeEntity {

  @Id
  @Column(name = "metric_code", length = 20)
  private String metricCode;

  @Column(name = "name_ko", length = 50)
  private String nameKo;

  @Column(name = "name_en", length = 50)
  private String nameEn;

  @Column(length = 20)
  private String unit;

  @Convert(converter = MetricCategoryConverter.class)
  @Column(length = 20)
  private MetricCategory category;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "display_order")
  private int displayOrder;

  protected FinancialMetricCatalog() {}

  public FinancialMetricCatalog(String metricCode, String nameKo, String nameEn) {
    this.metricCode = metricCode;
    this.nameKo = nameKo;
    this.nameEn = nameEn;
  }

  public String getMetricCode() {
    return metricCode;
  }

  public String getNameKo() {
    return nameKo;
  }

  public String getNameEn() {
    return nameEn;
  }

  public String getUnit() {
    return unit;
  }

  public MetricCategory getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }
}
