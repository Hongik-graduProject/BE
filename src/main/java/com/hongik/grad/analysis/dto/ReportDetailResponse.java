package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/** Response for GET /analysis/reports/{report_id} — spec §5-3. */
public record ReportDetailResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("name") String name,
    @JsonProperty("title") String title,
    @JsonProperty("applied_theme") AppliedThemeResponse appliedTheme,
    @JsonProperty("source_disclosure") SourceDisclosureResponse sourceDisclosure,
    @JsonProperty("summary") String summary,
    @JsonProperty("expected_car") Double expectedCar,
    @JsonProperty("car_lower") Double carLower,
    @JsonProperty("car_upper") Double carUpper,
    @JsonProperty("metric_contributions") List<MetricContribution> metricContributions,
    @JsonProperty("key_findings") List<String> keyFindings,
    @JsonProperty("metrics") ReportMetrics metrics,
    @JsonProperty("stage1_model_version") String stage1ModelVersion,
    @JsonProperty("stage2_model_version") String stage2ModelVersion,
    @JsonProperty("is_read") boolean isRead,
    @JsonProperty("generated_at") OffsetDateTime generatedAt) {}
