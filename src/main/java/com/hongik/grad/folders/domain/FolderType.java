package com.hongik.grad.folders.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Folder kind. Persisted as the lowercase string the V1 schema's {@code CHECK} constraint accepts.
 */
public enum FolderType {
  DEFAULT,
  CUSTOM;

  /** JPA converter mapping enum names ↔ lowercase DB values. */
  @Converter(autoApply = false)
  public static class JpaConverter implements AttributeConverter<FolderType, String> {

    @Override
    public String convertToDatabaseColumn(FolderType attribute) {
      return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public FolderType convertToEntityAttribute(String dbData) {
      return dbData == null ? null : FolderType.valueOf(dbData.toUpperCase());
    }
  }
}
