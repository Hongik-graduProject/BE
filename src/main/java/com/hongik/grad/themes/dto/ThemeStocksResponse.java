package com.hongik.grad.themes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hongik.grad.analysis.dto.PaginationMeta;
import java.util.List;

/** GET /themes/{theme_id}/stocks 응답. */
public record ThemeStocksResponse(
    @JsonProperty("theme") ThemeSummaryResponse theme,
    @JsonProperty("items") List<ThemeStockItem> items,
    @JsonProperty("pagination") PaginationMeta pagination) {}
