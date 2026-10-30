package com.hongik.grad.analysis.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * One row of the ML model service response ({@code GET /api/v1/predictions/{code}}), mirroring its
 * {@code LatestPredictionItem} schema.
 *
 * <p>Only the fields BACKEND_HANDOFF.md §3 lists for UI display are mapped; the remaining payload
 * fields are ignored by the decoder. {@code modelAgreementScore} is a Ridge/XGBoost rank-agreement
 * score — per that handoff it must never be surfaced as a confidence or hit probability.
 */
public record MlPredictionJson(
    @JsonProperty("date") LocalDate date,
    @JsonProperty("code") String code,
    @JsonProperty("name") String name,
    @JsonProperty("close") Double close,
    @JsonProperty("predicted_excess_return_60d") Double predictedExcessReturn60d,
    @JsonProperty("predicted_percentile") Double predictedPercentile,
    @JsonProperty("signal") String signal,
    @JsonProperty("model_agreement_score") Double modelAgreementScore,
    @JsonProperty("scenario_price_if_kospi_flat") Double scenarioPriceIfKospiFlat,
    @JsonProperty("feature_coverage") Double featureCoverage) {}
