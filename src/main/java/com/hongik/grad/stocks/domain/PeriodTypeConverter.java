package com.hongik.grad.stocks.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PeriodTypeConverter implements AttributeConverter<PeriodType, String> {

  @Override
  public String convertToDatabaseColumn(PeriodType attribute) {
    return attribute == null ? null : attribute.getValue();
  }

  @Override
  public PeriodType convertToEntityAttribute(String dbData) {
    return dbData == null ? null : PeriodType.fromValue(dbData);
  }
}
