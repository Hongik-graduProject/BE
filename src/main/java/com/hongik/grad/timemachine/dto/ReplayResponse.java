package com.hongik.grad.timemachine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * 보고서 하나의 되감기 결과. series 는 기준일(0%)부터 종료일(또는 오늘·거래정지 직전)까지 누적 수익률(%)이다. actual_excess_return_pct 는
 * 60 거래일이 다 찼을 때만 채운다.
 */
public record ReplayResponse(
    @JsonProperty("report_id") Long reportId,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("disclosure_type") String disclosureType,
    @JsonProperty("as_of") LocalDate asOf,
    @JsonProperty("base_date") LocalDate baseDate,
    @JsonProperty("end_date") LocalDate endDate,
    @JsonProperty("expected_car") Double expectedCar,
    @JsonProperty("actual_excess_return_pct") Double actualExcessReturnPct,
    @JsonProperty("status") String status,
    @JsonProperty("days_remaining") Integer daysRemaining,
    @JsonProperty("series") List<Point> series) {

  public record Point(
      @JsonProperty("date") LocalDate date,
      @JsonProperty("stock_return_pct") double stockReturnPct,
      @JsonProperty("kospi_return_pct") double kospiReturnPct) {}
}
