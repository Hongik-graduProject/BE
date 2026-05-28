package com.hongik.grad.themes.etl;

import com.hongik.grad.themes.application.ThemeIngestService;
import com.hongik.grad.themes.etl.dto.Stage1PredictionJson;
import com.hongik.grad.themes.etl.dto.Stage2CoefficientJson;
import com.hongik.grad.themes.etl.dto.ThemeCatalogJson;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ThemeSenseEtlRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(ThemeSenseEtlRunner.class);

  private final ArtifactsLoader artifactsLoader;
  private final ThemeIngestService themeIngestService;

  @Value("${themesense.artifacts-dir:}")
  private String defaultArtifactsDir;

  @Value("${themesense.stage1-version:themesense-stage1-v1.0}")
  private String defaultStage1Version;

  @Value("${themesense.stage2-version:themesense-stage2-v1.0}")
  private String defaultStage2Version;

  public ThemeSenseEtlRunner(
      ArtifactsLoader artifactsLoader, ThemeIngestService themeIngestService) {
    this.artifactsLoader = artifactsLoader;
    this.themeIngestService = themeIngestService;
  }

  @Override
  public void run(String... args) throws Exception {
    if (!hasFlag(args, "--etl-themesense")) {
      return;
    }

    String artifactsDir = parseOption(args, "--artifacts-dir", defaultArtifactsDir);
    String stage1Version = parseOption(args, "--stage1-version", defaultStage1Version);
    String stage2Version = parseOption(args, "--stage2-version", defaultStage2Version);

    if (artifactsDir == null || artifactsDir.isBlank()) {
      throw new IllegalArgumentException(
          "--artifacts-dir is required or set THEMESENSE_ARTIFACTS_DIR");
    }

    log.info(
        "Starting ThemeSense ETL: dir={}, stage1={}, stage2={}",
        artifactsDir,
        stage1Version,
        stage2Version);

    Path dir = Path.of(artifactsDir);
    List<ThemeCatalogJson> catalog = artifactsLoader.loadCatalog(dir);
    List<Stage1PredictionJson> stage1 = artifactsLoader.loadStage1(dir);
    List<Stage2CoefficientJson> stage2 = artifactsLoader.loadStage2(dir);

    themeIngestService.ingest(catalog, stage1, stage2, stage1Version, stage2Version);

    log.info("ThemeSense ETL completed");
  }

  private boolean hasFlag(String[] args, String flag) {
    for (String arg : args) {
      if (flag.equals(arg)) {
        return true;
      }
    }
    return false;
  }

  private String parseOption(String[] args, String key, String defaultValue) {
    for (int i = 0; i < args.length - 1; i++) {
      if (key.equals(args[i])) {
        return args[i + 1];
      }
    }
    return defaultValue;
  }
}
