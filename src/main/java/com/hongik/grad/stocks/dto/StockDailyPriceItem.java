package com.hongik.grad.stocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record StockDailyPriceItem(
    LocalDate date,
    Long close,
    Long change,
    Long open,
    Long high,
    Long low,
    Long volume) {

  @JsonProperty("change_rate")
  public Double changeRate() {
    if (close == null || change == null || close == 0L) return null;
    long prev = close - change;
    if (prev == 0L) return null;
    return ((double) change / prev) * 100.0;
  }
}
