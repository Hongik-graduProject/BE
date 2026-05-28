package com.hongik.grad.themes.application;

import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.ThemeMetricSensitivity;
import com.hongik.grad.themes.domain.ThemeMetricSensitivityRepository;
import com.hongik.grad.themes.dto.SensitivityView;
import com.hongik.grad.themes.dto.StockThemeView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ThemeQueryService {

  private final StockThemeRepository stockThemeRepository;
  private final ThemeMetricSensitivityRepository themeMetricSensitivityRepository;

  public ThemeQueryService(
      StockThemeRepository stockThemeRepository,
      ThemeMetricSensitivityRepository themeMetricSensitivityRepository) {
    this.stockThemeRepository = stockThemeRepository;
    this.themeMetricSensitivityRepository = themeMetricSensitivityRepository;
  }

  public List<StockThemeView> findTop3ByTicker(String ticker) {
    List<StockTheme> stockThemes = stockThemeRepository.findByStockTickerOrderByRankAsc(ticker);
    return stockThemes.stream().limit(3).map(this::toStockThemeView).toList();
  }

  public List<SensitivityView> findTop5SensitivityByThemeAndVersion(
      Long themeId, String version) {
    List<ThemeMetricSensitivity> sensitivities =
        themeMetricSensitivityRepository.findByThemeIdAndModelVersionOrderByRankAsc(
            themeId, version);
    return sensitivities.stream().limit(5).map(this::toSensitivityView).toList();
  }

  public Optional<StockThemeView> findPrimaryThemeByTicker(String ticker) {
    return findTop3ByTicker(ticker).stream().filter(StockThemeView::isPrimary).findFirst();
  }

  private StockThemeView toStockThemeView(StockTheme st) {
    return new StockThemeView(
        st.getTheme().getId(),
        st.getTheme().getName(),
        st.getRank(),
        st.getProbability(),
        st.isPrimary());
  }

  private SensitivityView toSensitivityView(ThemeMetricSensitivity s) {
    return new SensitivityView(
        s.getMetricCode(), s.getBeta(), s.getAbsBeta(), s.getRank(), s.getDirection());
  }
}
