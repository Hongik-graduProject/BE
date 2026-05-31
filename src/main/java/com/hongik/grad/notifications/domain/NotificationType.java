package com.hongik.grad.notifications.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Notification kind. Persisted as the lowercase string the V1 schema's {@code CHECK} constraint
 * accepts. v3 ships a single kind; new kinds extend the enum and the CHECK constraint.
 */
public enum NotificationType {
  DISCLOSURE_REPORT;

  /** JPA converter mapping enum names ↔ lowercase DB values. */
  @Converter(autoApply = false)
  public static class JpaConverter implements AttributeConverter<NotificationType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationType attribute) {
      return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public NotificationType convertToEntityAttribute(String dbData) {
      return dbData == null ? null : NotificationType.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
  }
}
