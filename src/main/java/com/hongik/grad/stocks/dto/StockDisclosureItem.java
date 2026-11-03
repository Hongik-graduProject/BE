package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StockDisclosureItem(
    Long id,
    String title,
    String type,
    @JsonProperty("filed_at") OffsetDateTime filedAt,
    @JsonProperty("dart_url") String dartUrl,
    // 이 공시로 만든 분석 보고서. 아직 없으면 둘 다 null.
    @JsonProperty("report_id") Long reportId,
    @JsonProperty("expected_car") BigDecimal expectedCar) {}
