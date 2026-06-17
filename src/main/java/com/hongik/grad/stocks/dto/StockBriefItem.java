package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 검색·자동완성·인기 등에서 공유되는 종목 요약. */
public record StockBriefItem(
    String ticker,
    String name,
    String market,
    String category,
    @JsonProperty("current_price") Long currentPrice,
    @JsonProperty("change_rate") Double changeRate,
    @JsonProperty("change_amount") Long changeAmount) {}
