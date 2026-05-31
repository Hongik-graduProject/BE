package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** §3-8 PATCH /folders/items/{item_id} response: id/memo/avg_price only. */
public record FavoriteUpdateResponse(
    Long id, String memo, @JsonProperty("avg_price") Long avgPrice) {}
