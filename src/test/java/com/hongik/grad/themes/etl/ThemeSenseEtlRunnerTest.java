package com.hongik.grad.themes.etl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.themes.application.ThemeIngestService;
import com.hongik.grad.themes.etl.dto.Stage1PredictionJson;
import com.hongik.grad.themes.etl.dto.Stage2CoefficientJson;
import com.hongik.grad.themes.etl.dto.ThemeCatalogJson;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ThemeSenseEtlRunnerTest {

  @Mock private ArtifactsLoader artifactsLoader;

  @Mock private ThemeIngestService themeIngestService;

  @InjectMocks private ThemeSenseEtlRunner runner;

  @Test
  @DisplayName("--etl-themesense 인자가 없으면 ETL을 실행하지 않는다")
  void skipWhenNoFlag() throws Exception {
    runner.run("--other-flag");

    verify(themeIngestService, never()).ingest(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("--etl-themesense 인자가 있으면 ETL을 실행한다")
  void runWhenFlagPresent() throws Exception {
    String dir = "/tmp/artifacts";
    ReflectionTestUtils.setField(runner, "defaultArtifactsDir", "");
    ReflectionTestUtils.setField(runner, "defaultStage1Version", "s1-v1");
    ReflectionTestUtils.setField(runner, "defaultStage2Version", "s2-v1");

    List<ThemeCatalogJson> catalog = List.of(new ThemeCatalogJson(0, "AI", "desc", "src"));
    List<Stage1PredictionJson> stage1 = List.of();
    List<Stage2CoefficientJson> stage2 = List.of();

    when(artifactsLoader.loadCatalog(Path.of(dir))).thenReturn(catalog);
    when(artifactsLoader.loadStage1(Path.of(dir))).thenReturn(stage1);
    when(artifactsLoader.loadStage2(Path.of(dir))).thenReturn(stage2);

    runner.run("--etl-themesense", "--artifacts-dir", dir);

    verify(themeIngestService).ingest(catalog, stage1, stage2, "s1-v1", "s2-v1");
  }

  @Test
  @DisplayName("--version 인자로 버전을 지정하면 해당 버전으로 ETL을 실행한다")
  void customVersionFromArgs() throws Exception {
    String dir = "/tmp/artifacts";
    ReflectionTestUtils.setField(runner, "defaultArtifactsDir", "");
    ReflectionTestUtils.setField(runner, "defaultStage1Version", "default-s1");
    ReflectionTestUtils.setField(runner, "defaultStage2Version", "default-s2");

    List<ThemeCatalogJson> catalog = List.of();
    List<Stage1PredictionJson> stage1 = List.of();
    List<Stage2CoefficientJson> stage2 = List.of();

    when(artifactsLoader.loadCatalog(Path.of(dir))).thenReturn(catalog);
    when(artifactsLoader.loadStage1(Path.of(dir))).thenReturn(stage1);
    when(artifactsLoader.loadStage2(Path.of(dir))).thenReturn(stage2);

    runner.run(
        "--etl-themesense",
        "--artifacts-dir",
        dir,
        "--stage1-version",
        "custom-s1",
        "--stage2-version",
        "custom-s2");

    verify(themeIngestService).ingest(catalog, stage1, stage2, "custom-s1", "custom-s2");
  }
}
