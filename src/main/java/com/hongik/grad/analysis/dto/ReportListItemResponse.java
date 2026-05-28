package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** One item in the reports list — spec §5-2. applied_theme is brief (id+name only). */
public record ReportListItemResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("title") String title,
    @JsonProperty("summary") String summary,
    @JsonProperty("applied_theme") AppliedThemeBriefResponse appliedTheme,
    @JsonProperty("expected_car") Double expectedCar,
    @JsonProperty("is_read") boolean isRead,
    @JsonProperty("generated_at") OffsetDateTime generatedAt) {}
