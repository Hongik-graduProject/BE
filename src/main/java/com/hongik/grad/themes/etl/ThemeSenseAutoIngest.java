package com.hongik.grad.themes.etl;

import com.hongik.grad.stocks.event.StocksBootstrappedEvent;
import com.hongik.grad.themes.application.ThemeIngestService;
import com.hongik.grad.themes.domain.StockThemeRepository;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 종목 적재가 끝난 뒤 {@code themesense.artifacts-dir} 의 산출물을 한 번 적재한다. stock_themes 가 이미 있으면 건너뛴다. 산출물을 다시
 * 넣으려면 {@link ThemeSenseEtlRunner} 를 {@code --etl-themesense} 로 실행한다.
 *
 * <p>종목이 먼저 있어야 stage1 을 넣을 수 있어서 부팅 시점이 아니라 적재 완료 이벤트에 붙는다.
 */
@Component
public class ThemeSenseAutoIngest {

  private static final Logger log = LoggerFactory.getLogger(ThemeSenseAutoIngest.class);

  private final ArtifactsLoader artifactsLoader;
  private final ThemeIngestService themeIngestService;
  private final StockThemeRepository stockThemeRepository;
  private final String artifactsDir;
  private final String stage1Version;
  private final String stage2Version;

  public ThemeSenseAutoIngest(
      ArtifactsLoader artifactsLoader,
      ThemeIngestService themeIngestService,
      StockThemeRepository stockThemeRepository,
      @Value("${themesense.artifacts-dir:}") String artifactsDir,
      @Value("${themesense.stage1-version:themesense-stage1-v1.0}") String stage1Version,
      @Value("${themesense.stage2-version:themesense-stage2-v1.0}") String stage2Version) {
    this.artifactsLoader = artifactsLoader;
    this.themeIngestService = themeIngestService;
    this.stockThemeRepository = stockThemeRepository;
    this.artifactsDir = artifactsDir;
    this.stage1Version = stage1Version;
    this.stage2Version = stage2Version;
  }

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onStocksBootstrapped(StocksBootstrappedEvent event) {
    if (artifactsDir == null || artifactsDir.isBlank()) {
      return;
    }
    if (stockThemeRepository.count() > 0) {
      log.info("ThemeSense auto-ingest skipped: stock_themes already populated");
      return;
    }
    try {
      Path dir = Path.of(artifactsDir);
      themeIngestService.ingest(
          artifactsLoader.loadCatalog(dir),
          artifactsLoader.loadStage1(dir),
          artifactsLoader.loadStage2(dir),
          stage1Version,
          stage2Version);
    } catch (RuntimeException ex) {
      log.warn("ThemeSense auto-ingest failed for dir={}: {}", artifactsDir, ex.getMessage(), ex);
    }
  }
}
