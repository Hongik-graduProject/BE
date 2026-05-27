package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalysisSummary(
    @JsonProperty("is_analyzed") boolean isAnalyzed,
    @JsonProperty("analysis_status") String analysisStatus) {}
