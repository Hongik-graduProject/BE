package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/** Response for GET /analysis/{ticker}/themes — spec §5-1. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThemesResponse(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("name") String name,
    @JsonProperty("themes") List<ThemeItemResponse> themes,
    @JsonProperty("model_version") String modelVersion,
    @JsonProperty("predicted_at") OffsetDateTime predictedAt,
    @JsonProperty("message") String message) {}
