package com.hongik.grad.themes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DirectionConverter implements AttributeConverter<Direction, String> {

  @Override
  public String convertToDatabaseColumn(Direction attribute) {
    return attribute == null ? null : attribute.getValue();
  }

  @Override
  public Direction convertToEntityAttribute(String dbData) {
    return dbData == null ? null : Direction.fromValue(dbData);
  }
}
