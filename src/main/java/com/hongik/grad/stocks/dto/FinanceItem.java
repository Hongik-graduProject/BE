package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** 한 (period_type, period, sj_div) 단위의 재무 항목. 비어있는 필드는 응답에서 제외. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinanceItem(
    @JsonProperty("period_type") String periodType,
    String period,
    @JsonProperty("sj_div") String sjDiv,
    @JsonProperty("fs_div") String fsDiv,

    // 손익계산서
    Long revenue,
    @JsonProperty("cost_of_revenue") Long costOfRevenue,
    @JsonProperty("gross_profit") Long grossProfit,
    Long sga,
    @JsonProperty("operating_profit") Long operatingProfit,
    @JsonProperty("non_operating_income") Long nonOperatingIncome,
    @JsonProperty("non_operating_expense") Long nonOperatingExpense,
    @JsonProperty("pretax_income") Long pretaxIncome,
    @JsonProperty("tax_expense") Long taxExpense,
    @JsonProperty("net_income") Long netIncome,
    @JsonProperty("controlling_net_income") Long controllingNetIncome,
    @JsonProperty("noncontrolling_net_income") Long noncontrollingNetIncome,

    // 재무상태표
    @JsonProperty("current_assets") Long currentAssets,
    @JsonProperty("non_current_assets") Long nonCurrentAssets,
    @JsonProperty("total_assets") Long totalAssets,
    @JsonProperty("current_liabilities") Long currentLiabilities,
    @JsonProperty("non_current_liabilities") Long nonCurrentLiabilities,
    @JsonProperty("total_liabilities") Long totalLiabilities,
    @JsonProperty("capital_stock") Long capitalStock,
    @JsonProperty("retained_earnings") Long retainedEarnings,
    @JsonProperty("total_equity") Long totalEquity,

    // 현금흐름표
    @JsonProperty("operating_cash_flow") Long operatingCashFlow,
    @JsonProperty("investing_cash_flow") Long investingCashFlow,
    @JsonProperty("financing_cash_flow") Long financingCashFlow,
    @JsonProperty("ending_cash") Long endingCash,

    // 비율 지표
    BigDecimal per,
    BigDecimal pbr,
    Long eps,
    @JsonProperty("dividend_yield") BigDecimal dividendYield,
    BigDecimal roe,
    BigDecimal roa) {}
