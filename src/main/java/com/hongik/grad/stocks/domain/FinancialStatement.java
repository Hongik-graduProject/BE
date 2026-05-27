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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "financial_statements")
public class FinancialStatement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticker", nullable = false)
  private Stock stock;

  @Column(name = "period_type", nullable = false, length = 10)
  private PeriodType periodType;

  @Column(nullable = false, length = 10)
  private String period;

  private Long revenue;

  @Column(name = "cost_of_revenue")
  private Long costOfRevenue;

  @Column(name = "gross_profit")
  private Long grossProfit;

  @Column(name = "operating_profit")
  private Long operatingProfit;

  @Column(name = "net_income")
  private Long netIncome;

  @Column(name = "total_assets")
  private Long totalAssets;

  @Column(name = "total_liabilities")
  private Long totalLiabilities;

  @Column(name = "total_equity")
  private Long totalEquity;

  @Column(precision = 10, scale = 2)
  private BigDecimal per;

  @Column(precision = 10, scale = 2)
  private BigDecimal pbr;

  private Long eps;

  @Column(name = "dividend_yield", precision = 5, scale = 2)
  private BigDecimal dividendYield;

  @Column(name = "data_source", length = 50)
  private String dataSource;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected FinancialStatement() {}

  public FinancialStatement(Stock stock, PeriodType periodType, String period) {
    this.stock = stock;
    this.periodType = periodType;
    this.period = period;
  }

  public Long getId() {
    return id;
  }

  public Stock getStock() {
    return stock;
  }

  public PeriodType getPeriodType() {
    return periodType;
  }

  public String getPeriod() {
    return period;
  }

  public Long getRevenue() {
    return revenue;
  }

  public Long getCostOfRevenue() {
    return costOfRevenue;
  }

  public Long getGrossProfit() {
    return grossProfit;
  }

  public Long getOperatingProfit() {
    return operatingProfit;
  }

  public Long getNetIncome() {
    return netIncome;
  }

  public Long getTotalAssets() {
    return totalAssets;
  }

  public Long getTotalLiabilities() {
    return totalLiabilities;
  }

  public Long getTotalEquity() {
    return totalEquity;
  }

  public BigDecimal getPer() {
    return per;
  }

  public BigDecimal getPbr() {
    return pbr;
  }

  public Long getEps() {
    return eps;
  }

  public BigDecimal getDividendYield() {
    return dividendYield;
  }

  public String getDataSource() {
    return dataSource;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
