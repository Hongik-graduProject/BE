package com.hongik.grad.timemachine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 종목 모델 전적. judged = hits + misses (중립·대기·자료부족 제외). hitRate 는 0~1 비율이고 judged < 10 이면 null 이다 (표본이
 * 적은 비율은 오해를 부른다).
 */
public record TrackRecord(
    @JsonProperty("total") int total,
    @JsonProperty("judged") int judged,
    @JsonProperty("hits") int hits,
    @JsonProperty("hit_rate") Double hitRate,
    @JsonProperty("neutral") int neutral,
    @JsonProperty("pending") int pending,
    @JsonProperty("insufficient") int insufficient) {}
