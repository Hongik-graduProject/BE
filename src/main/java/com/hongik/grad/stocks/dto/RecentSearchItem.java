package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record RecentSearchItem(
    Long id, String keyword, @JsonProperty("searched_at") OffsetDateTime searchedAt) {}
