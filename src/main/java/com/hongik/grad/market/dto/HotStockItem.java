package com.hongik.grad.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 최근 공시가 많은 종목. */
public record HotStockItem(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("name") String name,
    @JsonProperty("market") String market,
    @JsonProperty("disclosure_count") long disclosureCount) {}
