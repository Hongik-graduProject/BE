package com.hongik.grad.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 시장 지수 한 개. 하락이면 change / change_rate 가 음수. */
public record MarketIndexResponse(
    @JsonProperty("code") String code,
    @JsonProperty("name") String name,
    @JsonProperty("value") double value,
    @JsonProperty("change") double change,
    @JsonProperty("change_rate") double changeRate) {}
