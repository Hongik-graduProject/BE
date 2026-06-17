package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record StockNewsItem(
    Long id,
    String title,
    String source,
    String url,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("published_at") OffsetDateTime publishedAt) {}
