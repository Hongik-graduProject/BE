package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * ML model forecast for one ticker — spec addition for the company analysis tab.
 *
 * <p>{@code available} is false when the model service is unconfigured, unreachable, or holds no
 * row for the ticker; every other field is then null. The forecast is a 60-trading-day return
 * <em>relative to KOSPI</em>, and {@code model_agreement_score} is a Ridge/XGBoost agreement rank,
 * not a probability of being right.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockPredictionResponse(
    @JsonProperty("available") boolean available,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("as_of_date") LocalDate asOfDate,
    @JsonProperty("close") Double close,
    @JsonProperty("predicted_excess_return_60d") Double predictedExcessReturn60d,
    @JsonProperty("predicted_percentile") Double predictedPercentile,
    @JsonProperty("signal") String signal,
    @JsonProperty("model_agreement_score") Double modelAgreementScore,
    @JsonProperty("scenario_price_if_kospi_flat") Double scenarioPriceIfKospiFlat,
    @JsonProperty("feature_coverage") Double featureCoverage) {}
