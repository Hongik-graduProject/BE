package com.hongik.grad.themes.application;

import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.themes.domain.Direction;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeMetricSensitivity;
import com.hongik.grad.themes.domain.ThemeMetricSensitivityRepository;
import com.hongik.grad.themes.domain.ThemeRepository;
import com.hongik.grad.themes.etl.dto.Stage1PredictionJson;
import com.hongik.grad.themes.etl.dto.Stage2CoefficientJson;
import com.hongik.grad.themes.etl.dto.ThemeCatalogJson;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeIngestService {

  private static final Logger log = LoggerFactory.getLogger(ThemeIngestService.class);

  private final ThemeRepository themeRepository;
  private final StockThemeRepository stockThemeRepository;
  private final ThemeMetricSensitivityRepository sensitivityRepository;
  private final StockRepository stockRepository;
  private final Clock clock;

  public ThemeIngestService(
      ThemeRepository themeRepository,
      StockThemeRepository stockThemeRepository,
      ThemeMetricSensitivityRepository sensitivityRepository,
      StockRepository stockRepository,
      Clock clock) {
    this.themeRepository = themeRepository;
    this.stockThemeRepository = stockThemeRepository;
    this.sensitivityRepository = sensitivityRepository;
    this.stockRepository = stockRepository;
    this.clock = clock;
  }

  @Transactional
  public void ingest(
      List<ThemeCatalogJson> catalog,
      List<Stage1PredictionJson> predictions,
      List<Stage2CoefficientJson> coefficients,
      String stage1Version,
      String stage2Version) {

    Map<String, Theme> themesByName = upsertThemes(catalog);
    int stockThemeCount = upsertStockThemes(predictions, themesByName, stage1Version);
    int sensitivityCount = upsertSensitivities(coefficients, themesByName, stage2Version);

    log.info(
        "Ingested {} themes, {} stock_themes, {} sensitivities",
        themesByName.size(),
        stockThemeCount,
        sensitivityCount);
  }

  private Map<String, Theme> upsertThemes(List<ThemeCatalogJson> catalog) {
    Map<String, Theme> result = new HashMap<>();
    for (ThemeCatalogJson entry : catalog) {
      Theme theme =
          themeRepository
              .findByName(entry.name())
              .orElseGet(
                  () -> {
                    Theme newTheme = new Theme(entry.name());
                    newTheme.setSource(entry.source() != null ? entry.source() : "unknown");
                    return themeRepository.save(newTheme);
                  });
      theme.setDescription(entry.description());
      theme.setSource(entry.source() != null ? entry.source() : "unknown");
      theme.setStage1ClassIdx(entry.classIdx());
      result.put(entry.name(), theme);
    }
    return result;
  }

  private int upsertStockThemes(
      List<Stage1PredictionJson> predictions, Map<String, Theme> themesByName, String version) {
    int count = 0;
    for (Stage1PredictionJson prediction : predictions) {
      Optional<Stock> stockOpt = stockRepository.findById(prediction.ticker());
      if (stockOpt.isEmpty()) {
        log.warn("Stock not found, skipping: {}", prediction.ticker());
        continue;
      }
      Stock stock = stockOpt.get();

      for (Stage1PredictionJson.Prediction p : prediction.predictions()) {
        Theme theme = themesByName.get(p.themeName());
        if (theme == null) {
          log.warn("Theme not found in catalog, skipping: {}", p.themeName());
          continue;
        }

        Optional<StockTheme> existing =
            stockThemeRepository.findByStockTickerAndThemeIdAndModelVersion(
                stock.getTicker(), theme.getId(), version);
        if (existing.isPresent()) {
          StockTheme st = existing.get();
          st.setProbability(p.probability());
          st.setRank((short) p.rank());
          st.setPrimary(p.rank() == 1);
          st.setModelVersion(version);
          st.setPredictedAt(OffsetDateTime.now(clock));
        } else {
          StockTheme st = new StockTheme(stock, theme, (short) p.rank());
          st.setProbability(p.probability());
          st.setPrimary(p.rank() == 1);
          st.setModelVersion(version);
          st.setPredictedAt(OffsetDateTime.now(clock));
          stockThemeRepository.save(st);
          count++;
        }
      }
    }
    return count;
  }

  private int upsertSensitivities(
      List<Stage2CoefficientJson> coefficients, Map<String, Theme> themesByName, String version) {
    int count = 0;
    for (Stage2CoefficientJson entry : coefficients) {
      Theme theme = themesByName.get(entry.themeName());
      if (theme == null) {
        log.warn("Theme not found in catalog, skipping sensitivity: {}", entry.themeName());
        continue;
      }

      for (Stage2CoefficientJson.Coefficient c : entry.coefficients()) {
        Optional<String> canonicalOpt = MetricCodeNormalizer.toCatalogKey(c.metricCode());
        if (canonicalOpt.isEmpty()) {
          log.warn(
              "metric_code not in catalog, skipping sensitivity: theme={} code={}",
              entry.themeName(),
              c.metricCode());
          continue;
        }
        String canonicalCode = canonicalOpt.get();
        Optional<ThemeMetricSensitivity> existing =
            sensitivityRepository.findByThemeIdAndMetricCodeAndModelVersion(
                theme.getId(), canonicalCode, version);
        if (existing.isPresent()) {
          ThemeMetricSensitivity s = existing.get();
          s.setBeta(c.beta());
          s.setAbsBeta(c.absBeta());
          s.setRank(c.rank());
          s.setDirection(Direction.fromValue(c.direction()));
          s.setRSquared(c.rSquared());
          s.setSampleSize(c.sampleSize());
          s.setModelVersion(version);
          s.setComputedAt(OffsetDateTime.now(clock));
        } else {
          ThemeMetricSensitivity s =
              new ThemeMetricSensitivity(theme, canonicalCode, c.beta(), c.absBeta(), c.rank());
          s.setDirection(Direction.fromValue(c.direction()));
          s.setRSquared(c.rSquared());
          s.setSampleSize(c.sampleSize());
          s.setModelVersion(version);
          s.setComputedAt(OffsetDateTime.now(clock));
          sensitivityRepository.save(s);
          count++;
        }
      }
    }
    return count;
  }
}
