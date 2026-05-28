package com.hongik.grad.themes.domain;

public enum MetricCategory {
  VALUATION("valuation"),
  PROFITABILITY("profitability"),
  GROWTH("growth"),
  SOLVENCY("solvency"),
  LIQUIDITY("liquidity"),
  MARKET("market");

  private final String value;

  MetricCategory(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static MetricCategory fromValue(String value) {
    for (MetricCategory c : values()) {
      if (c.value.equals(value)) {
        return c;
      }
    }
    throw new IllegalArgumentException("Unknown metric category: " + value);
  }
}
