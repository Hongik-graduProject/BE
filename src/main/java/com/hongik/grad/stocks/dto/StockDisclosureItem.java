package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record StockDisclosureItem(
    Long id,
    String title,
    String type,
    @JsonProperty("filed_at") OffsetDateTime filedAt,
    @JsonProperty("dart_url") String dartUrl) {}
