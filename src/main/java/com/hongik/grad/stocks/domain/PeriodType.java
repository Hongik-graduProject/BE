package com.hongik.grad.stocks.domain;

public enum PeriodType {
  QUARTERLY("quarterly"),
  ANNUAL("annual");

  private final String value;

  PeriodType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static PeriodType fromValue(String value) {
    for (PeriodType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown period type: " + value);
  }
}
