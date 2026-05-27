package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FavoriteUpdateRequest(String memo, @JsonProperty("avg_price") Long avgPrice) {}
