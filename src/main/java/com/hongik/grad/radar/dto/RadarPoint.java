package com.hongik.grad.radar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * One disclosure drawn on the radar scope. {@code radius} and {@code is_mine} are relative to the
 * caller; {@code event} is set only on SSE pushes ("disclosure" | "report").
 */
public record RadarPoint(
    @JsonProperty("disclosure_id") Long disclosureId,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("name") String name,
    @JsonProperty("title") String title,
    @JsonProperty("type") String type,
    @JsonProperty("theme") String theme,
    @JsonProperty("theta") double theta,
    @JsonProperty("radius") double radius,
    @JsonProperty("expected_car") Double expectedCar,
    @JsonProperty("top_metric") String topMetric,
    @JsonProperty("filed_at") OffsetDateTime filedAt,
    @JsonProperty("is_mine") boolean isMine,
    @JsonProperty("event") String event) {}
