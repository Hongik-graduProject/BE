package com.hongik.grad.themes.etl.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Stage1PredictionJson(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("predictions") List<Prediction> predictions) {

  public record Prediction(
      @JsonProperty("class_idx") int classIdx,
      @JsonProperty("theme_name") String themeName,
      @JsonProperty("probability") double probability,
      @JsonProperty("rank") int rank) {}
}
