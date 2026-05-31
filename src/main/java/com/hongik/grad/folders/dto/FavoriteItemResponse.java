package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.stocks.dto.KrxQuote;
import java.time.OffsetDateTime;

public record FavoriteItemResponse(
    Long id,
    String ticker,
    String name,
    @JsonProperty("current_price") Long currentPrice,
    @JsonProperty("change_rate") Double changeRate,
    @JsonProperty("change_amount") Long changeAmount,
    String memo,
    @JsonProperty("avg_price") Long avgPrice,
    @JsonProperty("unread_disclosure_count") int unreadDisclosureCount,
    @JsonProperty("analysis_summary") AnalysisSummary analysisSummary,
    @JsonProperty("added_at") OffsetDateTime addedAt) {

  /**
   * Full §3-6 list item; cross-domain fields (name/unread/analysis_summary) supplied by service.
   */
  public static FavoriteItemResponse forList(
      Favorite favorite,
      String name,
      KrxQuote quote,
      int unreadDisclosureCount,
      AnalysisSummary analysisSummary) {
    return new FavoriteItemResponse(
        favorite.getId(),
        favorite.getTicker(),
        name,
        quote.currentPrice(),
        quote.changeRate(),
        quote.changeAmount(),
        favorite.getMemo(),
        favorite.getAvgPrice(),
        unreadDisclosureCount,
        analysisSummary,
        favorite.getAddedAt());
  }
}
