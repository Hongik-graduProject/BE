package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Top-5 sensitive metric entry for the primary theme. Exposes only spec §5-1 fields. */
public record SensitiveMetricResponse(
    @JsonProperty("code") String code,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("beta") double beta,
    @JsonProperty("rank") int rank) {}
