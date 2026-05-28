package com.hongik.grad.themes.domain;

public enum Direction {
  POSITIVE("positive"),
  NEGATIVE("negative"),
  ZERO("zero");

  private final String value;

  Direction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static Direction fromValue(String value) {
    for (Direction d : values()) {
      if (d.value.equals(value)) {
        return d;
      }
    }
    throw new IllegalArgumentException("Unknown direction: " + value);
  }
}
