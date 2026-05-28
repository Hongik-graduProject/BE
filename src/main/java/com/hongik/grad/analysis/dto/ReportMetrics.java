package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Financial metrics snapshot stored in the {@code metrics} JSONB column. Keys match spec §5-3. All
 * fields are nullable (v1.0 best-effort population from FinancialStatement raw values).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportMetrics(
    @JsonProperty("revenue") Long revenue,
    @JsonProperty("operating_profit") Long operatingProfit,
    @JsonProperty("net_income") Long netIncome,
    @JsonProperty("yoy_revenue_growth") Double yoyRevenueGrowth,
    @JsonProperty("yoy_op_growth") Double yoyOpGrowth) {}
