package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AppliedThemeResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("name") String name,
    @JsonProperty("routing_score") Double routingScore) {}
