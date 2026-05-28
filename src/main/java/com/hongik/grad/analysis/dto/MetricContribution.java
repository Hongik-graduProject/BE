package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row of the {@code metric_contributions} JSONB column. Fields match spec §5-3: metric_code,
 * display_name, beta, raw_value (nullable), delta, contribution. direction is intentionally omitted
 * from external exposure.
 */
public record MetricContribution(
    @JsonProperty("metric_code") String metricCode,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("beta") double beta,
    @JsonProperty("raw_value") String rawValue,
    @JsonProperty("delta") double delta,
    @JsonProperty("contribution") double contribution) {}
