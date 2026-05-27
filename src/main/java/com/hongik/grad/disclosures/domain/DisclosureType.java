package com.hongik.grad.disclosures.domain;

public enum DisclosureType {
  REPORT("report"),
  QUARTERLY("quarterly"),
  INTERIM("interim"),
  EVENT("event");

  private final String value;

  DisclosureType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static DisclosureType fromValue(String value) {
    for (DisclosureType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown disclosure type: " + value);
  }
}
