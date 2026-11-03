package com.hongik.grad.themes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 테마 탐색 — 테마 하나와 그 테마를 1순위로 가진 종목 수. */
public record ThemeSummaryResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("stock_count") long stockCount) {}
