package com.hongik.grad.themes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hongik.grad.themes.domain.Direction;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeMetricSensitivity;
import com.hongik.grad.themes.domain.ThemeMetricSensitivityRepository;
import com.hongik.grad.themes.dto.SensitivityView;
import com.hongik.grad.themes.dto.StockThemeView;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThemeQueryServiceTest {

  @Mock private StockThemeRepository stockThemeRepository;
  @Mock private ThemeMetricSensitivityRepository themeMetricSensitivityRepository;

  private ThemeQueryService service;

  @BeforeEach
  void setUp() {
    service = new ThemeQueryService(stockThemeRepository, themeMetricSensitivityRepository);
  }

  @Test
  @DisplayName("종목의 Top-3 테마를 rank 순으로 반환한다")
  void findTop3ByTicker_returnsOrderedThemes() throws Exception {
    Theme t1 = createTheme(1L, "AI/반도체");
    Theme t2 = createTheme(2L, "2차전지/EV");
    Theme t3 = createTheme(3L, "바이오/헬스케어");

    StockTheme st1 = createStockTheme(t1, (short) 1, 0.85, true);
    StockTheme st2 = createStockTheme(t2, (short) 2, 0.10, false);
    StockTheme st3 = createStockTheme(t3, (short) 3, 0.05, false);

    when(stockThemeRepository.findByStockTickerOrderByRankAsc("005930"))
        .thenReturn(List.of(st1, st2, st3));

    List<StockThemeView> result = service.findTop3ByTicker("005930");

    assertThat(result).hasSize(3);
    assertThat(result.get(0).themeName()).isEqualTo("AI/반도체");
    assertThat(result.get(0).rank()).isEqualTo((short) 1);
    assertThat(result.get(0).probability()).isEqualTo(0.85);
    assertThat(result.get(0).isPrimary()).isTrue();
    assertThat(result.get(1).themeName()).isEqualTo("2차전지/EV");
    assertThat(result.get(2).themeName()).isEqualTo("바이오/헬스케어");
  }

  @Test
  @DisplayName("Primary 테마를 올바르게 반환한다")
  void findPrimaryThemeByTicker_returnsPrimary() throws Exception {
    Theme t1 = createTheme(1L, "AI/반도체");
    StockTheme st1 = createStockTheme(t1, (short) 1, 0.85, true);

    when(stockThemeRepository.findByStockTickerOrderByRankAsc("005930")).thenReturn(List.of(st1));

    Optional<StockThemeView> result = service.findPrimaryThemeByTicker("005930");

    assertThat(result).isPresent();
    assertThat(result.get().themeId()).isEqualTo(1L);
    assertThat(result.get().isPrimary()).isTrue();
  }

  @Test
  @DisplayName("테마가 없으면 빈 Optional을 반환한다")
  void findPrimaryThemeByTicker_returnsEmptyWhenNoThemes() {
    when(stockThemeRepository.findByStockTickerOrderByRankAsc("999999")).thenReturn(List.of());

    Optional<StockThemeView> result = service.findPrimaryThemeByTicker("999999");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("테마별 Top-5 민감지표를 rank 순으로 반환한다")
  void findTop5SensitivityByThemeAndVersion_returnsOrderedSensitivities() throws Exception {
    Theme theme = createTheme(1L, "AI/반도체");
    ThemeMetricSensitivity s1 = createSensitivity(theme, "PER", 0.35, 0.35, 1, Direction.POSITIVE);
    ThemeMetricSensitivity s2 = createSensitivity(theme, "ROE", -0.28, 0.28, 2, Direction.NEGATIVE);

    when(themeMetricSensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(1L, "v1.0"))
        .thenReturn(List.of(s1, s2));

    List<SensitivityView> result = service.findTop5SensitivityByThemeAndVersion(1L, "v1.0");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).metricCode()).isEqualTo("PER");
    assertThat(result.get(0).beta()).isEqualTo(0.35);
    assertThat(result.get(0).direction()).isEqualTo(Direction.POSITIVE);
    assertThat(result.get(1).metricCode()).isEqualTo("ROE");
    assertThat(result.get(1).direction()).isEqualTo(Direction.NEGATIVE);
  }

  private Theme createTheme(Long id, String name) throws Exception {
    Theme theme = new Theme(name);
    setField(theme, "id", id);
    return theme;
  }

  private StockTheme createStockTheme(Theme theme, short rank, double probability, boolean primary)
      throws Exception {
    StockTheme st = new StockTheme(null, theme, rank);
    setField(st, "probability", probability);
    setField(st, "isPrimary", primary);
    return st;
  }

  private ThemeMetricSensitivity createSensitivity(
      Theme theme, String metricCode, double beta, double absBeta, int rank, Direction direction)
      throws Exception {
    ThemeMetricSensitivity s = new ThemeMetricSensitivity(theme, metricCode, beta, absBeta, rank);
    setField(s, "direction", direction);
    return s;
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Class<?> clazz = target.getClass();
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
