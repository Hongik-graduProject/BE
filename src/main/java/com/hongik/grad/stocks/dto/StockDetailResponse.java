package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("is_favorite") boolean isFavorite) {}
