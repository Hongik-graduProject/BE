package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record StockDetailResponse(
    String ticker,
    String name,
    String market,
    String category,
    String description,
    @JsonProperty("listed_at") LocalDate listedAt,
    @JsonProperty("current_price") Long currentPrice,
    @JsonProperty("change_rate") Double changeRate,
    @JsonProperty("change_amount") Long changeAmount,
    @JsonProperty("is_favorite") boolean isFavorite,
    // 아래 지표는 네이버 조회 실패 시 null.
    @JsonProperty("market_cap") Long marketCap,
    BigDecimal per,
    BigDecimal pbr,
    @JsonProperty("dividend_yield") BigDecimal dividendYield,
    @JsonProperty("high_52w") Long high52w,
    @JsonProperty("low_52w") Long low52w) {}
