package com.hongik.grad.themes.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.themes.etl.dto.Stage1PredictionJson;
import com.hongik.grad.themes.etl.dto.Stage2CoefficientJson;
import com.hongik.grad.themes.etl.dto.ThemeCatalogJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ArtifactsLoader {

  private static final Logger log = LoggerFactory.getLogger(ArtifactsLoader.class);

  private static final String CATALOG_FILE = "themes_catalog.json";
  private static final String STAGE1_FILE = "stage1_predictions.json";
  private static final String STAGE2_FILE = "stage2_coefficients.json";

  private final ObjectMapper objectMapper;

  public ArtifactsLoader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ThemeCatalogJson> loadCatalog(Path dir) {
    return load(dir, CATALOG_FILE, new TypeReference<>() {});
  }

  public List<Stage1PredictionJson> loadStage1(Path dir) {
    return load(dir, STAGE1_FILE, new TypeReference<>() {});
  }

  public List<Stage2CoefficientJson> loadStage2(Path dir) {
    return load(dir, STAGE2_FILE, new TypeReference<>() {});
  }

  private <T> T load(Path dir, String filename, TypeReference<T> typeRef) {
    Path file = dir.resolve(filename);
    if (!Files.exists(file)) {
      throw new IllegalStateException("Required artifact not found: " + file);
    }
    log.info("Loading artifact: {}", file);
    try {
      return objectMapper.readValue(file.toFile(), typeRef);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse artifact: " + file, e);
    }
  }
}
