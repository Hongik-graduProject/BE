package com.hongik.grad.disclosures.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DisclosureTypeConverter implements AttributeConverter<DisclosureType, String> {

  @Override
  public String convertToDatabaseColumn(DisclosureType attribute) {
    return attribute == null ? null : attribute.getValue();
  }

  @Override
  public DisclosureType convertToEntityAttribute(String dbData) {
    return dbData == null ? null : DisclosureType.fromValue(dbData);
  }
}
