package com.hongik.grad.themes.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.themes.etl.dto.Stage1PredictionJson;
import com.hongik.grad.themes.etl.dto.Stage2CoefficientJson;
import com.hongik.grad.themes.etl.dto.ThemeCatalogJson;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArtifactsLoaderTest {

  private ArtifactsLoader loader;
  private Path fixtureDir;

  @BeforeEach
  void setUp() {
    loader = new ArtifactsLoader(new ObjectMapper());
    fixtureDir = Paths.get("src/test/resources/fixtures/themesense");
  }

  @Test
  @DisplayName("themes_catalog.json을 올바르게 파싱한다")
  void loadCatalog_parsesCorrectly() {
    List<ThemeCatalogJson> catalog = loader.loadCatalog(fixtureDir);

    assertThat(catalog).hasSize(3);
    ThemeCatalogJson first = catalog.get(0);
    assertThat(first.classIdx()).isZero();
    assertThat(first.name()).isEqualTo("AI/반도체");
    assertThat(first.description()).isEqualTo("AI 및 반도체 관련 기업");
    assertThat(first.source()).isEqualTo("ThemeSense v1");
  }

  @Test
  @DisplayName("stage1_predictions.json을 올바르게 파싱한다")
  void loadStage1_parsesCorrectly() {
    List<Stage1PredictionJson> predictions = loader.loadStage1(fixtureDir);

    assertThat(predictions).hasSize(2);
    Stage1PredictionJson samsung = predictions.get(0);
    assertThat(samsung.ticker()).isEqualTo("005930");
    assertThat(samsung.predictions()).hasSize(3);

    Stage1PredictionJson.Prediction top = samsung.predictions().get(0);
    assertThat(top.classIdx()).isZero();
    assertThat(top.themeName()).isEqualTo("AI/반도체");
    assertThat(top.probability()).isEqualTo(0.85);
    assertThat(top.rank()).isEqualTo(1);
  }

  @Test
  @DisplayName("stage2_coefficients.json을 올바르게 파싱한다")
  void loadStage2_parsesCorrectly() {
    List<Stage2CoefficientJson> coefficients = loader.loadStage2(fixtureDir);

    assertThat(coefficients).hasSize(2);
    Stage2CoefficientJson ai = coefficients.get(0);
    assertThat(ai.themeName()).isEqualTo("AI/반도체");
    assertThat(ai.modelVersion()).isEqualTo("v1.0");
    assertThat(ai.coefficients()).hasSize(3);

    Stage2CoefficientJson.Coefficient first = ai.coefficients().get(0);
    assertThat(first.metricCode()).isEqualTo("PER");
    assertThat(first.beta()).isEqualTo(0.35);
    assertThat(first.absBeta()).isEqualTo(0.35);
    assertThat(first.rank()).isEqualTo(1);
    assertThat(first.direction()).isEqualTo("positive");
    assertThat(first.rSquared()).isEqualTo(0.72);
    assertThat(first.sampleSize()).isEqualTo(150);
  }

  @Test
  @DisplayName("파일이 없으면 IllegalStateException을 던진다")
  void missingFile_throwsException() {
    Path nonExistent = Paths.get("src/test/resources/fixtures/nonexistent");

    assertThatThrownBy(() -> loader.loadCatalog(nonExistent))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Required artifact not found");
  }
}
