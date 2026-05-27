package com.hongik.grad.stocks.dto;

public record KrxQuote(String ticker, long currentPrice, double changeRate, long changeAmount) {

  public static KrxQuote empty(String ticker) {
    return new KrxQuote(ticker, 0, 0.0, 0);
  }
}
