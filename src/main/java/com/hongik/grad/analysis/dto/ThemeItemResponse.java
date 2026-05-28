package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ThemeItemResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("name") String name,
    @JsonProperty("rank") int rank,
    @JsonProperty("probability") double probability,
    @JsonProperty("is_primary") boolean isPrimary,
    @JsonProperty("top_sensitive_metrics") List<SensitiveMetricResponse> topSensitiveMetrics) {}
