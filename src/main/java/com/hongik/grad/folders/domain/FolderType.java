package com.hongik.grad.folders.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Folder kind. Persisted as the lowercase string the V1 schema's {@code CHECK} constraint accepts,
 * and serialized to JSON in lowercase per spec §3.
 */
public enum FolderType {
  DEFAULT,
  CUSTOM;

  /** Lowercase wire value for JSON serialization (e.g. {@code "default"}, {@code "custom"}). */
  @JsonValue
  public String json() {
    return name().toLowerCase(Locale.ROOT);
  }

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
