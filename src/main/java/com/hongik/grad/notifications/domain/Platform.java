package com.hongik.grad.notifications.domain;

import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * FCM device platform. Persisted as the lowercase string the V1 schema's {@code CHECK} constraint
 * accepts.
 */
public enum Platform {
  IOS,
  ANDROID;

  public static Platform fromValue(String value) {
    if (value == null) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "platform must not be null");
    }
    try {
      return Platform.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "Unknown platform: " + value);
    }
  }

  /** JPA converter mapping enum names ↔ lowercase DB values. */
  @Converter(autoApply = false)
  public static class JpaConverter implements AttributeConverter<Platform, String> {

    @Override
    public String convertToDatabaseColumn(Platform attribute) {
      return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Platform convertToEntityAttribute(String dbData) {
      return dbData == null ? null : Platform.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
  }
}
