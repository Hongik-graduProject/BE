package com.hongik.grad.themes.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeMetricSensitivityRepository
    extends JpaRepository<ThemeMetricSensitivity, Long> {

  List<ThemeMetricSensitivity> findByThemeIdAndModelVersionOrderByRankAsc(
      Long themeId, String modelVersion);

  Optional<ThemeMetricSensitivity> findByThemeIdAndMetricCodeAndModelVersion(
      Long themeId, String metricCode, String modelVersion);
}
