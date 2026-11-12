package com.hongik.grad.timemachine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/** 전적 필드는 최상위에 펼친다. type 은 요청 필터(없으면 null), by_type 은 필터와 무관한 유형별 전적이다. */
public record TrackRecordResponse(
    @JsonProperty("ticker") String ticker,
    @JsonProperty("type") String type,
    @JsonUnwrapped TrackRecord record,
    @JsonProperty("by_type") List<TypeTrackRecord> byType) {

  public record TypeTrackRecord(
      @JsonProperty("type") String type, @JsonUnwrapped TrackRecord record) {}
}
