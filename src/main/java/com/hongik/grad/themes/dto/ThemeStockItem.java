package com.hongik.grad.themes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 테마 소속 종목 — 시세는 실패 시 null. */
public record ThemeStockItem(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("name") String name,
    @JsonProperty("market") String market,
    @JsonProperty("current_price") Long currentPrice,
    @JsonProperty("change_rate") Double changeRate,
    @JsonProperty("change_amount") Long changeAmount,
    @JsonProperty("probability") double probability) {}
