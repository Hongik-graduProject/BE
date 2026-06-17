package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record FinanceItem(
    @JsonProperty("period_type") String periodType,
    String period,
    Long revenue,
    @JsonProperty("cost_of_revenue") Long costOfRevenue,
    @JsonProperty("gross_profit") Long grossProfit,
    @JsonProperty("operating_profit") Long operatingProfit,
    @JsonProperty("net_income") Long netIncome,
    @JsonProperty("total_assets") Long totalAssets,
    @JsonProperty("total_liabilities") Long totalLiabilities,
    @JsonProperty("total_equity") Long totalEquity,
    BigDecimal per,
    BigDecimal pbr,
    Long eps,
    @JsonProperty("dividend_yield") BigDecimal dividendYield) {}
