package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Brief applied_theme for list items — only id and name, no routing_score. Spec §5-2. */
public record AppliedThemeBriefResponse(
    @JsonProperty("id") Long id, @JsonProperty("name") String name) {}
