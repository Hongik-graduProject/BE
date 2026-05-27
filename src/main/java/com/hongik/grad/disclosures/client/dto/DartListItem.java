package com.hongik.grad.disclosures.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DartListItem(
    String corpCode,
    String corpName,
    String stockCode,
    String reportNm,
    String rceptNo,
    String flrNm,
    String rceptDt,
    String rm) {}
