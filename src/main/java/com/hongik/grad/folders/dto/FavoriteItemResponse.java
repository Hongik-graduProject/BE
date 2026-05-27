package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hongik.grad.folders.domain.Favorite;
import java.time.OffsetDateTime;

public record FavoriteItemResponse(
    Long id,
    String ticker,
    String memo,
    @JsonProperty("avg_price") Long avgPrice,
    @JsonProperty("current_price") Long currentPrice,
    @JsonProperty("change_rate") Double changeRate,
    @JsonProperty("change_amount") Long changeAmount,
    @JsonProperty("analysis_summary") AnalysisSummary analysisSummary,
    @JsonProperty("added_at") OffsetDateTime addedAt) {

  public static FavoriteItemResponse from(Favorite favorite) {
    return new FavoriteItemResponse(
        favorite.getId(),
        favorite.getTicker(),
        favorite.getMemo(),
        favorite.getAvgPrice(),
        null,
        null,
        null,
        new AnalysisSummary(false, "테마 분류 대기 중"),
        favorite.getAddedAt());
  }
}
