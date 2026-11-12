package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One self-critique of a report (반박 모드). {@code severity} is {@code "warning"} (a real weakness
 * fired) or {@code "info"} (a structural limit or an unverifiable source). {@code evidence} carries
 * the raw numbers behind the summary.
 */
public record CritiqueItem(
    @JsonProperty("code") String code,
    @JsonProperty("severity") String severity,
    @JsonProperty("summary") String summary,
    @JsonProperty("evidence") String evidence) {}
