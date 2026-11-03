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

  // Mirrors the V8 column DEFAULT 'CIS'; Hibernate writes an explicit NULL otherwise.
  @Column(name = "sj_div", length = 4, nullable = false)
  private String sjDiv = "CIS";

  @Column(name = "fs_div", length = 4)
  private String fsDiv;

  // 손익계산서 확장
  @Column private Long sga;

  @Column(name = "non_operating_income")
  private Long nonOperatingIncome;

  @Column(name = "non_operating_expense")
  private Long nonOperatingExpense;

  @Column(name = "pretax_income")
  private Long pretaxIncome;

  @Column(name = "tax_expense")
  private Long taxExpense;

  @Column(name = "controlling_net_income")
  private Long controllingNetIncome;

  @Column(name = "noncontrolling_net_income")
  private Long noncontrollingNetIncome;

  // 재무상태표 확장
  @Column(name = "current_assets")
  private Long currentAssets;

  @Column(name = "non_current_assets")
  private Long nonCurrentAssets;

  @Column(name = "current_liabilities")
  private Long currentLiabilities;

  @Column(name = "non_current_liabilities")
  private Long nonCurrentLiabilities;

  @Column(name = "capital_stock")
  private Long capitalStock;

  @Column(name = "retained_earnings")
  private Long retainedEarnings;

  // 현금흐름표
  @Column(name = "operating_cash_flow")
  private Long operatingCashFlow;

  @Column(name = "investing_cash_flow")
  private Long investingCashFlow;

  @Column(name = "financing_cash_flow")
  private Long financingCashFlow;

  @Column(name = "ending_cash")
  private Long endingCash;

  // 비율 지표
  @Column(precision = 7, scale = 4)
  private BigDecimal roe;

  @Column(precision = 7, scale = 4)
  private BigDecimal roa;

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

  public String getSjDiv() {
    return sjDiv;
  }

  public String getFsDiv() {
    return fsDiv;
  }

  public Long getSga() {
    return sga;
  }

  public Long getNonOperatingIncome() {
    return nonOperatingIncome;
  }

  public Long getNonOperatingExpense() {
    return nonOperatingExpense;
  }

  public Long getPretaxIncome() {
    return pretaxIncome;
  }

  public Long getTaxExpense() {
    return taxExpense;
  }

  public Long getControllingNetIncome() {
    return controllingNetIncome;
  }

  public Long getNoncontrollingNetIncome() {
    return noncontrollingNetIncome;
  }

  public Long getCurrentAssets() {
    return currentAssets;
  }

  public Long getNonCurrentAssets() {
    return nonCurrentAssets;
  }

  public Long getCurrentLiabilities() {
    return currentLiabilities;
  }

  public Long getNonCurrentLiabilities() {
    return nonCurrentLiabilities;
  }

  public Long getCapitalStock() {
    return capitalStock;
  }

  public Long getRetainedEarnings() {
    return retainedEarnings;
  }

  public Long getOperatingCashFlow() {
    return operatingCashFlow;
  }

  public Long getInvestingCashFlow() {
    return investingCashFlow;
  }

  public Long getFinancingCashFlow() {
    return financingCashFlow;
  }

  public Long getEndingCash() {
    return endingCash;
  }

  public BigDecimal getRoe() {
    return roe;
  }

  public BigDecimal getRoa() {
    return roa;
  }

  public void assignSjDiv(String sjDiv) {
    this.sjDiv = sjDiv;
  }

  public void assignFsDiv(String fsDiv) {
    this.fsDiv = fsDiv;
  }

  /** DART fnlttSinglAcntAll 응답을 한 번에 흡수. null 인자는 변경하지 않는다. */
  public void applyDartFinance(
      Long revenue,
      Long costOfRevenue,
      Long grossProfit,
      Long operatingProfit,
      Long netIncome,
      Long totalAssets,
      Long totalLiabilities,
      Long totalEquity) {
    if (revenue != null) this.revenue = revenue;
    if (costOfRevenue != null) this.costOfRevenue = costOfRevenue;
    if (grossProfit != null) this.grossProfit = grossProfit;
    if (operatingProfit != null) this.operatingProfit = operatingProfit;
    if (netIncome != null) this.netIncome = netIncome;
    if (totalAssets != null) this.totalAssets = totalAssets;
    if (totalLiabilities != null) this.totalLiabilities = totalLiabilities;
    if (totalEquity != null) this.totalEquity = totalEquity;
    this.dataSource = "DART";
  }

  /** 손익계산서 라인 일괄 적용. null은 무시. */
  public void applyIncomeStatement(
      Long revenue,
      Long costOfRevenue,
      Long grossProfit,
      Long sga,
      Long operatingProfit,
      Long nonOperatingIncome,
      Long nonOperatingExpense,
      Long pretaxIncome,
      Long taxExpense,
      Long netIncome,
      Long controllingNetIncome,
      Long noncontrollingNetIncome) {
    if (revenue != null) this.revenue = revenue;
    if (costOfRevenue != null) this.costOfRevenue = costOfRevenue;
    if (grossProfit != null) this.grossProfit = grossProfit;
    if (sga != null) this.sga = sga;
    if (operatingProfit != null) this.operatingProfit = operatingProfit;
    if (nonOperatingIncome != null) this.nonOperatingIncome = nonOperatingIncome;
    if (nonOperatingExpense != null) this.nonOperatingExpense = nonOperatingExpense;
    if (pretaxIncome != null) this.pretaxIncome = pretaxIncome;
    if (taxExpense != null) this.taxExpense = taxExpense;
    if (netIncome != null) this.netIncome = netIncome;
    if (controllingNetIncome != null) this.controllingNetIncome = controllingNetIncome;
    if (noncontrollingNetIncome != null) this.noncontrollingNetIncome = noncontrollingNetIncome;
    this.dataSource = "DART";
  }

  /** 재무상태표 라인 일괄 적용. null은 무시. */
  public void applyBalanceSheet(
      Long currentAssets,
      Long nonCurrentAssets,
      Long totalAssets,
      Long currentLiabilities,
      Long nonCurrentLiabilities,
      Long totalLiabilities,
      Long capitalStock,
      Long retainedEarnings,
      Long totalEquity) {
    if (currentAssets != null) this.currentAssets = currentAssets;
    if (nonCurrentAssets != null) this.nonCurrentAssets = nonCurrentAssets;
    if (totalAssets != null) this.totalAssets = totalAssets;
    if (currentLiabilities != null) this.currentLiabilities = currentLiabilities;
    if (nonCurrentLiabilities != null) this.nonCurrentLiabilities = nonCurrentLiabilities;
    if (totalLiabilities != null) this.totalLiabilities = totalLiabilities;
    if (capitalStock != null) this.capitalStock = capitalStock;
    if (retainedEarnings != null) this.retainedEarnings = retainedEarnings;
    if (totalEquity != null) this.totalEquity = totalEquity;
    this.dataSource = "DART";
  }

  /** 현금흐름표 라인 일괄 적용. null은 무시. */
  public void applyCashFlow(
      Long operatingCashFlow, Long investingCashFlow, Long financingCashFlow, Long endingCash) {
    if (operatingCashFlow != null) this.operatingCashFlow = operatingCashFlow;
    if (investingCashFlow != null) this.investingCashFlow = investingCashFlow;
    if (financingCashFlow != null) this.financingCashFlow = financingCashFlow;
    if (endingCash != null) this.endingCash = endingCash;
    this.dataSource = "DART";
  }

  /** 비율 지표 적용. */
  public void applyRatios(
      BigDecimal per,
      BigDecimal pbr,
      Long eps,
      BigDecimal dividendYield,
      BigDecimal roe,
      BigDecimal roa) {
    if (per != null) this.per = per;
    if (pbr != null) this.pbr = pbr;
    if (eps != null) this.eps = eps;
    if (dividendYield != null) this.dividendYield = dividendYield;
    if (roe != null) this.roe = roe;
    if (roa != null) this.roa = roa;
  }
}
