package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response for GET /analysis/{ticker}/reports — spec §5-2. */
public record ReportListResponse(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("name") String name,
    @JsonProperty("reports") List<ReportListItemResponse> reports,
    @JsonProperty("pagination") PaginationMeta pagination) {}
