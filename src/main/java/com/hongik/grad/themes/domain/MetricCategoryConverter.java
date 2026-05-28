package com.hongik.grad.themes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MetricCategoryConverter implements AttributeConverter<MetricCategory, String> {

  @Override
  public String convertToDatabaseColumn(MetricCategory attribute) {
    return attribute == null ? null : attribute.getValue();
  }

  @Override
  public MetricCategory convertToEntityAttribute(String dbData) {
    return dbData == null ? null : MetricCategory.fromValue(dbData);
  }
}
