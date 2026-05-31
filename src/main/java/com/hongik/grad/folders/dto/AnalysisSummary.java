package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Per-item ThemeSense summary — spec §3-6. Two shapes share one record via NON_NULL: has_theme=true
 * carries applied_theme + report fields; has_theme=false carries only message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisSummary(
    @JsonProperty("has_theme") boolean hasTheme,
    @JsonProperty("applied_theme") AppliedTheme appliedTheme,
    @JsonProperty("latest_report_id") Long latestReportId,
    @JsonProperty("expected_car") Double expectedCar,
    @JsonProperty("summary") String summary,
    @JsonProperty("generated_at") OffsetDateTime generatedAt,
    @JsonProperty("message") String message) {

  public record AppliedTheme(@JsonProperty("id") Long id, @JsonProperty("name") String name) {}

  public static AnalysisSummary withReport(
      AppliedTheme appliedTheme,
      Long latestReportId,
      Double expectedCar,
      String summary,
      OffsetDateTime generatedAt) {
    return new AnalysisSummary(
        true, appliedTheme, latestReportId, expectedCar, summary, generatedAt, null);
  }

  public static AnalysisSummary themeOnly(AppliedTheme appliedTheme) {
    return new AnalysisSummary(true, appliedTheme, null, null, null, null, null);
  }

  public static AnalysisSummary noTheme(String message) {
    return new AnalysisSummary(false, null, null, null, null, null, message);
  }
}
