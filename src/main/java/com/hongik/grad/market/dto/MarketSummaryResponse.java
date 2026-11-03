package com.hongik.grad.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/** GET /market/summary 응답. 공시가 하나도 없으면 latest_* 는 null. */
public record MarketSummaryResponse(
    @JsonProperty("indices") List<MarketIndexResponse> indices,
    @JsonProperty("latest_disclosure_date") LocalDate latestDisclosureDate,
    @JsonProperty("latest_disclosure_count") Long latestDisclosureCount,
    @JsonProperty("hot_stocks") List<HotStockItem> hotStocks) {}
