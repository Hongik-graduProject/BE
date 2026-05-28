package com.hongik.grad.themes.etl.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Stage2CoefficientJson(
    @JsonProperty("theme_name") String themeName,
    @JsonProperty("model_version") String modelVersion,
    @JsonProperty("coefficients") List<Coefficient> coefficients) {

  public record Coefficient(
      @JsonProperty("metric_code") String metricCode,
      @JsonProperty("beta") double beta,
      @JsonProperty("abs_beta") double absBeta,
      @JsonProperty("rank") int rank,
      @JsonProperty("direction") String direction,
      @JsonProperty("r_squared") Double rSquared,
      @JsonProperty("sample_size") Integer sampleSize) {}
}
